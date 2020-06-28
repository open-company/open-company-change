(ns oc.change.config
  "Namespace for the configuration parameters."
  (:require [environ.core :refer (env)]))

(defn- bool
  "Handle the fact that we may have true/false strings, when we want booleans."
  [val]
  (boolean (Boolean/valueOf val)))

;; ----- System -----

(defonce processors (.availableProcessors (Runtime/getRuntime)))
(defonce core-async-limit (+ 42 (* 2 processors)))

(defonce prod? (= "production" (env :env)))
(defonce intro? (not prod?))

;; ----- Sentry -----

(defonce dsn (or (env :open-company-sentry-change) false))

;; ----- Logging (see https://github.com/ptaoussanis/timbre) -----

(defonce log-level (or (env :log-level) :info))

;; ----- DynamoDB -----

(defonce migrations-dir "./src/oc/change/db/migrations")
(defonce migration-template "./src/oc/change/assets/migration.template.edn")

(defonce dynamodb-end-point (or (env :dynamodb-end-point) "http://localhost:8000"))

(defonce dynamodb-table-prefix (or (env :dynamodb-table-prefix) "local"))

(defonce dynamodb-opts {
    :access-key (env :aws-access-key-id)
    :secret-key (env :aws-secret-access-key)
    :endpoint dynamodb-end-point
    :table-prefix dynamodb-table-prefix
  })

;; ----- HTTP server -----

(defonce hot-reload (bool (or (env :hot-reload) false)))
(defonce change-server-port (Integer/parseInt (or (env :port) "3006")))
(defonce ensure-origin (bool (or (env :oc-ws-ensure-origin) true)))
(defonce ui-server-url (or (env :ui-server-url) "http://localhost:3559"))

;; ----- AWS SQS -----

(defonce aws-access-key-id (env :aws-access-key-id))
(defonce aws-secret-access-key (env :aws-secret-access-key))

(defonce aws-sqs-change-queue (env :aws-sqs-change-queue))

;; ----- JWT -----

(defonce passphrase (env :open-company-auth-passphrase))

;; ----- Change service -----

(defonce draft-board-uuid "0000-0000-0000")
(defonce change-ttl (or  (read-string (env :oc-change-ttl)) (* 30 6))) ; 6 months
(defonce seen-ttl (or (read-string (env :oc-seen-ttl)) (* 30 6))) ; 6 months