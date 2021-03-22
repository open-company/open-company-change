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

(defn gsi-matches-type?
  "Returns true if the named GSI index has the same type specified, false otherwise."
  [dynamodb-opts index-name table-name index-type]
  (let [index-key          (keyword index-name)
        matches-index-key? #(when (= index-key (:name %)) %)
        table-desc         (far/describe-table dynamodb-opts table-name)
        gsindexes          (:gsindexes table-desc)
        index-data         (some matches-index-key? gsindexes)]
    (case index-type
      :all (= (-> index-data :projection :projection-type) "ALL")
      (:keys :keys-only) (= (-> index-data :projection :projection-type) "KEYS_ONLY"))))

(defn table-exists?
  "Return table description only if it exists"
  [dynamodb-opts table-name]
  (far/describe-table dynamodb-opts table-name))