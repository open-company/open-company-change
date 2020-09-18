(ns oc.change.components
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [oc.lib.sentry.core :refer (map->SentryCapturer)]
            [org.httpkit.server :as httpkit]
            [oc.lib.sqs :as sqs]
            [oc.lib.async.watcher :as watcher]
            [oc.change.api.websockets :as websockets-api]
            [oc.change.async.persistence :as persistence]))

(defrecord HttpKit [options handler]
  component/Lifecycle

  (start [component]
    (timbre/info "[http] starting...")
    (let [handler (get-in component [:handler :handler] handler)
          server  (httpkit/run-server handler options)]
      (websockets-api/start)
      (timbre/info "[http] started")
      (assoc component :http-kit server)))

  (stop [{:keys [http-kit] :as component}]
    (if http-kit
      (do
        (timbre/info "[http] stopping...")
        (http-kit)
        (websockets-api/stop)
        (timbre/info "[http] stopped")
        (dissoc component :http-kit))
      component)))

(defrecord Handler [handler-fn]
  component/Lifecycle

  (start [component]
    (timbre/info "[handler] started")
    (assoc component :handler (handler-fn component)))

  (stop [component]
    (timbre/info "[handler] stopped")
    (dissoc component :handler)))

(defrecord AsyncConsumers []
  component/Lifecycle

  (start [component]
    (timbre/info "[async-consumers] starting...")
    (persistence/start) ; core.async channel consumer for persisting events
    (watcher/start) ; core.async channel consumer for watched items (containers watched by websockets) events
    (timbre/info "[async-consumers] started")
    (assoc component :async-consumers true))

  (stop [{:keys [async-consumers] :as component}]
    (if async-consumers
      (do
        (timbre/info "[async-consumers] stopping...")
        (persistence/stop) ; core.async channel consumer for persisting events
        (watcher/stop) ; core.async channel consumer for watched items (containers watched by websockets) events
        (timbre/info "[async-consumers] stopped")
        (dissoc component :async-consumers))
    component)))

(defn change-system [{httpkit :httpkit sqs-consumer :sqs-consumer sentry :sentry}]
  (component/system-map
    :sentry-capturer (map->SentryCapturer sentry)
    :async-consumers (component/using
                        (map->AsyncConsumers {})
                        [:sentry-capturer])
    :sqs-consumer (component/using
                   (sqs/sqs-listener sqs-consumer)
                   [:sentry-capturer])
    :handler (component/using
                (map->Handler {:handler-fn (:handler-fn httpkit)})
                [:sentry-capturer])
    :server  (component/using
                (map->HttpKit {:options {:port (:port httpkit)}})
                [:handler])))

;; ----- REPL usage -----

(comment

  ;; To use the Interaction Service from the REPL
  (require '[com.stuartsierra.component :as component])
  (require '[oc.change.config :as config])
  (require '[oc.change.components :as components] :reload)
  (require '[oc.change.app :as app] :reload)

  (def change-service (components/change-system {:httpkit {:handler-fn app/app
                                                           :port config/change-server-port}
                                                :sqs-consumer {
                                                  :sqs-queue c/aws-sqs-change-queue
                                                  :message-handler app/sqs-handler
                                                  :sqs-creds {
                                                    :access-key c/aws-access-key-id
                                                    :secret-key c/aws-secret-access-key}}}))

  (def instance (component/start change-service))

  ;; if you need to change something, just stop the service

  (component/stop instance)

  ;; reload whatever namespaces you need to and start the service again, change the system if you want by re-def'ing
  ;; the `change-service` var above.

  (def instance (component/start change-service))

  )