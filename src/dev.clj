(ns dev
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :as ctnrepl]
            [oc.change.config :as c]
            [oc.change.app :as app]
            [oc.change.components :as components]))

(def system nil)
(def conn nil)

(defn init
  ([] (init c/change-server-port))
  ([port]
  (alter-var-root #'system (constantly (components/change-system {:httpkit {:handler-fn app/app
                                                                            :port port}
                                                                  :sqs-consumer {
                                                                    :sqs-queue c/aws-sqs-change-queue
                                                                    :message-handler app/sqs-handler
                                                                    :sqs-creds {
                                                                      :access-key c/aws-access-key-id
                                                                      :secret-key c/aws-secret-access-key}}})))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go
  
  ([] (go c/change-server-port))
  
  ([port]
  (init port)
  (start)
  (app/echo-config port)
  (println (str "Now serving changes from the REPL.\n"
                "When you're ready to stop the system, just type: (stop)\n"))
  port))

(defn reset []
  (stop)
  (ctnrepl/refresh :after 'user/go))