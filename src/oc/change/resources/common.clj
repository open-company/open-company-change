(ns oc.change.resources.common
  (:require [taoensso.faraday :as far]))

(defn gsi-exists-on-table?
  "Returns true if the global secondary index with the given name exists on the
  table named table-name, false otherwise."
  [dynamodb-opts index-name table-name]
  (let [index-key          (keyword index-name)
        matches-index-key? #(= index-key (:name %))
        table-desc         (far/describe-table dynamodb-opts table-name)
        gsindexes          (:gsindexes table-desc)]
    (some matches-index-key? gsindexes)))