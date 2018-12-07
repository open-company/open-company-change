(ns oc.change.util.ttl
  (:require [clj-time.core :as time]
            [clj-time.coerce :as coerce]))

(defn ttl-epoch
  "Given a ttl value, make sure it's an integer to avoid errors, and return the epoch
   of now plus the ttl in days."
  [ttl]
  (let [;; If ttl value is set from env var it's a string, if it's default is an int
        fixed-change-ttl (if (string? ttl)
                           (Integer. (re-find #"\d+" ttl))
                           ttl)
        ttl-date (time/plus (time/now) (time/days fixed-change-ttl))]
    (coerce/to-epoch ttl-date)))

(defn ttl-now
  "Return the current time in epoch."
  []
  (coerce/to-epoch (time/now)))