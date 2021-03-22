(ns oc.change.resources.change
  "Store tuples of: container-id, item-id and change timestamp, with a TTL"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [clojure.set :as clj-set]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]
            [oc.lib.dynamo.ttl :as ttl]))

(def table-name (keyword (str c/dynamodb-table-prefix "_change")))
(def container-id-item-id-gsi-name (str c/dynamodb-table-prefix "_change_gsi_container_id_item_id"))

(defn draft-id? [s]
  (if (and s
           (string? s)
           (re-matches #"^0000-0000-0000-(\d|[a-f]){4}-(\d|[a-f]){4}-(\d|[a-f]){4}$" s))
    true
    false))

(def DraftID (schema/pred draft-id?))

(defn unique-draft-id? [s]
  (or (draft-id? s)
      (lib-schema/unique-id? s)))

(def UniqueDraftID (schema/pred unique-draft-id?))

(schema/defn ^:always-validate retrieve :- [{:container-id UniqueDraftID :item-id UniqueDraftID :change-at lib-schema/ISO8601}]
  [container :- (schema/conditional sequential? [UniqueDraftID] :else UniqueDraftID)]
  (if (sequential? container)
    (flatten (pmap retrieve container))
    (->> (far/query c/dynamodb-opts table-name {:container_id [:eq container]}
          {:filter-expr "#k > :v"
           :expr-attr-names {"#k" "ttl"}
           :expr-attr-vals {":v" (ttl/ttl-now)}})
      (map #(clj-set/rename-keys % {:container_id :container-id :item_id :item-id :change_at :change-at}))
      (map #(select-keys % [:container-id :item-id :change-at])))))

(schema/defn ^:always-validate retrieve-by-item :- (schema/maybe {(schema/optional-key :container-id) UniqueDraftID
                                                                  (schema/optional-key :item-id) UniqueDraftID
                                                                  (schema/optional-key :change-at) lib-schema/ISO8601})
  [container-id :- UniqueDraftID item-id :- UniqueDraftID]
  (when-let [full-item (far/get-item c/dynamodb-opts table-name {:container_id container-id
                                                                 :item_id item-id})]
    (-> full-item
     (clj-set/rename-keys {:container_id :container-id :item_id :item-id :change_at :change-at})
     (select-keys [:container-id :item-id :change-at]))))

(schema/defn ^:always-validate store!
  [container-id :- UniqueDraftID item-id :- UniqueDraftID change-at :- lib-schema/ISO8601]
  (far/put-item c/dynamodb-opts table-name {
      :container_id container-id
      :item_id item-id
      :change_at change-at
      :ttl (ttl/ttl-epoch c/change-ttl)})
  true)

(schema/defn ^:always-validate delete-by-item!
  [container-id :- UniqueDraftID item-id :- lib-schema/UniqueID]
  (far/delete-item c/dynamodb-opts table-name {:container_id container-id
                                               :item_id item-id}))

(schema/defn ^:always-validate delete-by-container!
  [container-id :- UniqueDraftID]
  (doseq [item (far/query c/dynamodb-opts table-name {:container_id [:eq container-id]})]
    (far/delete-item c/dynamodb-opts table-name {:container_id container-id
                                                 :item_id (:item_id item)})))

(schema/defn ^:always-validate move-item!
  [item-id :- lib-schema/UniqueID old-container-id :- UniqueDraftID new-container-id :- UniqueDraftID]
  (when-let [full-item (retrieve-by-item old-container-id item-id)]
    (store! new-container-id item-id (:change-at full-item))
    (delete-by-item! old-container-id item-id)))

(comment

  (require '[oc.lib.time :as oc-time])
  (require '[oc.change.resources.change :as change] :reload)
  (require '[aprint.core :refer (aprint)])

  (far/list-tables c/dynamodb-opts)

  (far/delete-table c/dynamodb-opts change/table-name)
  (aprint
    (far/create-table c/dynamodb-opts
      change/table-name
      [:container_id :s]
      {:range-keydef [:item_id :s]
       :billing-mode :pay-per-request
       :block? true}))

  ;; GSI used for delete via container-id
  (aprint
    @(far/update-table config/dynamodb-opts
      change/table-name
      {:gsindexes {:operation :create
                   :name change/container-id-item-id-gsi-name
                   :billing-mode :pay-per-request
                   :hash-keydef [:item_id :s]
                   :range-keydef [:container_id :s]
                   :projection :keys-only}}))

  (doseq [item (far/query config/dynamodb-opts change/table-name {:item_id [:eq "512b-4ad1-9924"]} {:index change/container-id-item-id-gsi-name})]
    (aprint
      (far/delete-item config/dynamodb-opts change/table-name {:container_id (:container_id item)
                                                               :item_id (:item_id item)})))

  (aprint (far/describe-table c/dynamodb-opts change/table-name))

  (change/store! "1111-1111-1111" "2222-2222-2222" (oc-time/current-timestamp))
  (change/store! "1111-1111-1111" "3333-3333-3333" (oc-time/current-timestamp))
  (change/retrieve "1111-1111-1111")

  (change/store! "4444-4444-4444" "5555-5555-5555" (oc-time/current-timestamp))
  (change/retrieve ["1111-1111-1111" "4444-4444-4444"])

)