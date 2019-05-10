(ns oc.change.app
  "Namespace for the application which starts all the system components."
  (:gen-class)
  (:require
    [clojure.core.async :as async :refer (>!!)]
    [raven-clj.core :as sentry]
    [raven-clj.interfaces :as sentry-interfaces]
    [raven-clj.ring :as sentry-mw]
    [taoensso.timbre :as timbre]
    [cheshire.core :as json]
    [ring.logger.timbre :refer (wrap-with-logger)]
    [ring.middleware.keyword-params :refer (wrap-keyword-params)]
    [ring.middleware.params :refer (wrap-params)]
    [ring.middleware.reload :refer (wrap-reload)]
    [ring.middleware.cors :refer (wrap-cors)]
    [compojure.core :as compojure :refer (GET)]
    [com.stuartsierra.component :as component]
    [oc.lib.sentry-appender :as sa]
    [oc.change.components :as components]
    [oc.lib.sqs :as sqs]
    [oc.lib.async.watcher :as watcher]
    [oc.change.config :as c]
    [oc.change.api.websockets :as websockets-api]
    [oc.change.api.change :as change-api]
    [oc.change.async.persistence :as persistence]
    [oc.lib.middleware.wrap-ensure-origin :refer (wrap-ensure-origin)]))

;; ----- Unhandled Exceptions -----

;; Send unhandled exceptions to log and Sentry
;; See https://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (timbre/error ex "Uncaught exception on" (.getName thread) (.getMessage ex))
     (when c/dsn
       (sentry/capture c/dsn (-> {:message (.getMessage ex)}
                                 (assoc-in [:extra :exception-data] (ex-data ex))
                                 (sentry-interfaces/stacktrace ex)))))))

;; ----- SQS Incoming Request -----

(defn sqs-handler
  "
  Handle an incoming SQS message to the change service.

  {
    :notification-type 'add|update|delete',
    :notification-at ISO8601,
    :user {...},
    :org {...},
    :board {...},
    :content {:new {...},
              :old {...}}
  }
  "
  [msg done-channel]
  (doseq [body (sqs/read-message-body (:body msg))]
    (let [msg-body (json/parse-string (:Message body) true)
          change-type (keyword (:notification-type msg-body))
          resource-type (keyword (:resource-type msg-body))
          draft?   (or (= "draft" (-> msg-body :content :new :status))
                       (and (= change-type :delete)
                            (= "draft" (-> msg-body :content :old :status))))
          user-id (-> msg-body :user :user-id)
          container-id  (if draft?
                          (str c/draft-board-uuid "-" user-id) ;; attach author id
                          (or (-> msg-body :board :uuid) ; entry or board
                              (-> msg-body :org :uuid))) ; org
          payload-cont-id (if draft?
                            ;; remove user id from draft containter id
                            (clojure.string/replace container-id
                                                    (str "-" user-id) "")
                            container-id)
          item-id (or (-> msg-body :content :new :uuid) ; new or update
                      (-> msg-body :content :old :uuid)) ; delete
          change-at (or (-> msg-body :content :new :updated-at) ; add / update
                        (:notification-at msg-body))] ; delete
      (timbre/info "Received message from SQS:" msg-body)
      (if (and
           (or (= change-type :add) (= change-type :update) (= change-type :delete))
           (or (= resource-type :entry) (= resource-type :board)))

        ;; Add/update/delete of entry/board
        (do
          (timbre/info "Requesting persistence for entry add/update/delete msg from SQS.")
          (>!! persistence/persistence-chan (merge msg-body {:change true
                                                             :change-type change-type
                                                             :change-at change-at
                                                             :container-id container-id
                                                             :resource-type resource-type
                                                             :item-id item-id
                                                             :author-id user-id}))

          (timbre/info "Alerting watcher of add/update/delete msg from SQS.")
          (>!! watcher/watcher-chan {:send true
                                     :watch-id container-id
                                     :event (if (= resource-type :entry) :item/change :container/change)
                                     :payload {:container-id payload-cont-id
                                               :change-type change-type
                                               :item-id item-id
                                               :user-id user-id
                                               :change-at change-at}}))

        ;; Org or unknown
        (cond
         (= resource-type :org)
         (timbre/warn "Unhandled org message from SQS:" change-type resource-type)
         :default
         (timbre/warn "Unknown message from SQS:" change-type resource-type)))))
    (sqs/ack done-channel msg))

;; ----- Request Routing -----

(defn routes [sys]
  (compojure/routes
    (GET "/ping" [] {:body "OpenCompany Change Service: OK" :status 200}) ; Up-time monitor
    (GET "/---error-test---" [] (/ 1 0))
    (GET "/---500-test---" [] {:body "Testing bad things." :status 500})
    (websockets-api/routes sys)
    (change-api/routes sys)))

;; ----- System Startup -----

(defn echo-config [port]
  (println (str "\n"
    "Running on port: " port "\n"
    "Dynamo DB: " c/dynamodb-end-point "\n"
    "Table prefix: " c/dynamodb-table-prefix "\n"
    "Change TTL: " c/change-ttl " days\n"
    "Seen TTL: " c/seen-ttl " days\n"
    "AWS SQS change queue: " c/aws-sqs-change-queue "\n"
    "Hot-reload: " c/hot-reload "\n"
    "Ensure origin: " c/ensure-origin "\n"
    "Sentry: " c/dsn "\n\n"
    (when c/intro? "Ready to serve...\n"))))

;; Ring app definition
(defn app [sys]
  (cond-> (routes sys)
    c/dsn             (sentry-mw/wrap-sentry c/dsn) ; important that this is first
    c/prod?           wrap-with-logger
    true              wrap-keyword-params
    true              wrap-params
    true              (wrap-cors #".*")
    c/ensure-origin   wrap-ensure-origin
    c/hot-reload      wrap-reload))

(defn start
  "Start an instance of the service."
  [port]

  ;; Stuff logged at error level goes to Sentry
  (if c/dsn
    (timbre/merge-config!
      {:level (keyword c/log-level)
       :appenders {:sentry (sa/sentry-appender c/dsn)}})
    (timbre/merge-config! {:level (keyword c/log-level)}))

  ;; Start the system
  (-> {:httpkit {:handler-fn app :port port}
       :sqs-consumer {
          :sqs-queue c/aws-sqs-change-queue
          :message-handler sqs-handler
          :sqs-creds {:access-key c/aws-access-key-id
                      :secret-key c/aws-secret-access-key}}}
    components/change-system
    component/start)

  ;; Echo config information
  (println (str "\n"
    (when c/intro? (str (slurp (clojure.java.io/resource "ascii_art.txt")) "\n"))
    "OpenCompany Change Service\n"))
  (echo-config port))

(defn -main []
  (start c/change-server-port))
