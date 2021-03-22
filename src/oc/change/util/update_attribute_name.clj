(ns oc.change.util.update-attribute-name
  (:require [taoensso.faraday :as far]
            [cuerdas.core :as string]
            [taoensso.timbre :as timbre]
            [oc.change.config :as config]))

(defn- find-key [prim-keys find-key-type]
  (some (fn [[k v]] (when (= (:key-type v) find-key-type) (name k)))
        prim-keys))

(defn- find-keys [dopts table-name]
  (let [table-description (far/describe-table dopts table-name)
        prim-keys (:prim-keys table-description)
        hash-key (find-key prim-keys :hash)
        range-key (find-key prim-keys :range)]
    (timbre/tracef "%s table primary keys: %s" table-name prim-keys)
    {:hash-key hash-key
     :range-key range-key}))

(defn attr-not-exists-filter-expr
  ([filter-attr hash-key range-key limit] (attr-not-exists-filter-expr filter-attr hash-key range-key limit true))
  ([filter-attr hash-key range-key limit limit-projection?]
   (timbre/debugf "Creating attribute doesn't exists filter expression for attribute %s, with hash key %s and range key %s, limit %d%s"
                  filter-attr hash-key range-key limit (when limit-projection? " (limit projection to primary keys)"))
   (let [proj-expr (when limit-projection?
                     {:proj-expr "#pk1, #pk2"
                      :expr-attr-names {"#pk1" hash-key
                                        "#pk2" range-key}})
         merged-expr (merge-with merge
                                 {:filter-expr "attribute_not_exists(#fk)"
                                  :limit limit
                                  :expr-attr-names {"#fk" filter-attr}}
                                 proj-expr)]
     (timbre/debugf "Not existing expression: %s" merged-expr)
     merged-expr)))

(defn attr-exists-filter-expr
  [filter-attr hash-key range-key limit limit-projection?]
  (let [proj-expr (when limit-projection?
                    {:proj-expr "#pk1, #pk2"
                     :expr-attr-names {"#pk1" hash-key
                                       "#pk2" range-key}})
        limit-expr (when (pos? limit)
                     {:limit limit})
        merged-expr (merge-with merge
                                {:filter-expr "attribute_exists(#fk)"
                                 :expr-attr-names {"#fk" filter-attr}}
                                limit-expr
                                proj-expr)]
    (timbre/debugf "Attribute %s existing expression: %s" filter-attr merged-expr)
    merged-expr))

(defn table-scan-for-non-existing-attribute
  ([dynopts table-name filter-attr hash-key range-key] (table-scan-for-non-existing-attribute dynopts table-name filter-attr hash-key range-key true))
  ([dynopts table-name filter-attr hash-key range-key limit-projection?]
   (far/scan dynopts table-name
             (attr-not-exists-filter-expr filter-attr hash-key range-key limit-projection?))))

(defn table-scan-for-existing-attribute
  ([dynopts table-name filter-attr hash-key range-key limit] (table-scan-for-existing-attribute dynopts table-name filter-attr hash-key range-key limit false))
  ([dynopts table-name filter-attr hash-key range-key limit limit-projection?]
   (far/scan dynopts table-name (attr-exists-filter-expr filter-attr hash-key range-key limit limit-projection?))))

(defn- update-cond-expr [item in-attr out-attr]
  (let [set? (string/empty-or-nil? (get item (keyword out-attr)))
        update-expr (if set?
                      "REMOVE #kd SET #ka = :va"
                      "REMOVE #kd")
        expr-attr-names (if set?
                          {"#kd" in-attr
                           "#ka" out-attr}
                          {"#kd" in-attr})
        expr-map-base {:update-expr update-expr
                       :expr-attr-names expr-attr-names
                       :return :updated-new}
        expr-map (if set?
                   (merge expr-map-base {:expr-attr-vals {":va" (get item (keyword in-attr))}})
                   expr-map-base)]
    (timbre/tracef "%s attribute already preset in record? %s" out-attr set?)
    (timbre/tracef "Update condition expression %s" expr-map)
    expr-map))

(defn- update-item-sel [item hash-key range-key]
  (timbre/tracef "Updating item %s" item)
  (let [hash-key-kw (keyword hash-key)
        range-key-kw (keyword range-key)
        update-sel {hash-key-kw (get item hash-key-kw)
                    range-key-kw (get item range-key-kw)}]
    (timbre/tracef "Update selector: %s" update-sel)
    update-sel))

(defn update-attribute-name
  ([table-name in-attr out-attr] (update-attribute-name config/dynamodb-opts table-name in-attr out-attr {}))
  ([table-name in-attr out-attr params] (update-attribute-name config/dynamodb-opts table-name in-attr out-attr params))
  ([dynopts table-name in-attr out-attr {limit :limit run? :run? :or {limit 100} :as params}]
   (timbre/infof "Replacing attribute %s with %s in %s table, params: %s" in-attr out-attr table-name params)
   (let [{:keys [hash-key range-key]} (find-keys dynopts table-name)
         all-in-items (table-scan-for-existing-attribute dynopts table-name in-attr hash-key range-key limit)]
     (timbre/infof "Loaded %d items" (count all-in-items))
     (timbre/tracef "Loaded items: %s" all-in-items)
     (let [updated-items (when run?
                           (timbre/tracef "Updating items...")
                           (mapv (fn [item]
                                   (far/update-item dynopts table-name (update-item-sel item hash-key range-key) (update-cond-expr item in-attr out-attr)))
                                 all-in-items))]
       (timbre/tracef "Updated items: %s" updated-items)
       (timbre/infof "Updated %d items" (count updated-items))
       updated-items))))

(comment
  ;; Usage
  (require '[oc.change.utils.update-attribute-name :refer (update-attribute-name)])
  
  ;; Replace org-id with org_id in read table (limit to the first 100 records)
  (require '[oc.change.resources.read :as read])
  ;; Try a dry run first:
  (update-attribute-name read/table-name "org-id" "org_id" {:limit 100})
  ;; Apply the changes
  (update-attribute-name read/table-name "org-id" "org_id" {:limit 100 :run? true})
  
  ;; Replace org-id with org_id in read table (limit to the first 100 records)
  (require '[oc.change.resources.seen :as seen])
  ;; Try a dry run first:
  (update-attribute-name seen/table-name "user-id" "user_id" {:limit 100})
  ;; Apply the changes
  (update-attribute-name seen/table-name "user-id" "user_id" {:limit 100 :run? true})
  )