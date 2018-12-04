(ns oc.change.util.fix-ttl
  (:require [taoensso.faraday :as far]
            [clj-time.coerce :as coerce]
            [oc.lib.db.migrations :as m]
            [oc.change.config :as config]
            [oc.change.resources.change :as change]
            [oc.change.resources.seen :as seen]))

(defn fix-change-ttl []
  (println "Scanning " change/table-name)
  (let [results (far/scan config/dynamodb-opts change/table-name {:attr-conds {:ttl [:gt 9999999999]}})]
    (for [r results]
      (let [fixed-ttl (coerce/to-epoch (coerce/from-long (long (:ttl r))))]
        (println "   Fixing:" (:ttl r) "->" fixed-ttl)
        (far/update-item config/dynamodb-opts change/table-name
         {:container_id (:container_id r)
          :item_id (:item_id r)}
         {:update-expr "SET #k = :v"
          :expr-attr-names {"#k" "ttl"}
          :expr-attr-vals  {":v" fixed-ttl}
          :return :all-new})))))

(defn fix-seen-ttl []
  (println "Scanning " seen/table-name)
  (let [results (far/scan config/dynamodb-opts seen/table-name {:attr-conds {:ttl [:gt 9999999999]}})]
    (for [r results]
      (let [fixed-ttl (coerce/to-epoch (coerce/from-long (long (:ttl r))))]
        (println "   Fixing:" (:ttl r) "->" fixed-ttl)
        (far/update-item config/dynamodb-opts seen/table-name
         {:user_id (:user_id r)
          :container_item_id (:container_item_id r)}
         {:update-expr "SET #k = :v"
          :expr-attr-names {"#k" "ttl"}
          :expr-attr-vals  {":v" fixed-ttl}
          :return :all-new})))))