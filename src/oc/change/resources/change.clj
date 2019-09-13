(ns oc.change.resources.change
  "Store tuples of: container-id, item-id and change timestamp, with a TTL"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]
            [oc.lib.dynamo.common :as ttl]))

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

(schema/defn ^:always-validate store!
  [container-id :- UniqueDraftID item-id :- UniqueDraftID change-at :- lib-schema/ISO8601]
  (far/put-item c/dynamodb-opts table-name {
      :container_id container-id
      :item_id item-id
      :change_at change-at
      :ttl (ttl/ttl-epoch c/change-ttl)})
  true)

(schema/defn ^:always-validate delete-by-item!
  [container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (far/delete-item c/dynamodb-opts table-name {:container_id container-id
                                               :item_id item-id}))

(schema/defn ^:always-validate delete-by-container!
  [container-id :- lib-schema/UniqueID]
  (doseq [item (far/query c/dynamodb-opts table-name {:container_id [:eq container-id]})]
    (far/delete-item c/dynamodb-opts table-name {:container_id container-id
                                                 :item_id (:item_id item)})))

(schema/defn ^:always-validate move-item!
  [item-id :- lib-schema/UniqueID old-container-id :- lib-schema/UniqueID new-container-id :- lib-schema/UniqueID]
  (let [items-to-move (far/query c/dynamodb-opts table-name {:item_id [:eq item-id] :container_id [:eq old-container-id]})]
    (timbre/info "Change move-item! for" item-id " moving:" (count items-to-move) "from container" old-container-id "to" new-container-id)
    (doseq [item items-to-move]
      (let [full-item (far/get-item c/dynamodb-opts table-name {:container_id [:eq old-container-id] :item_id [:eq item-id]})]
        (far/delete-item c/dynamodb-opts table-name {:container_id (:container_id full-item)
                                                     :item_id (:item_id full-item)})
        (far/put-item c/dynamodb-opts table-name {
          :container_id new-container-id
          :item_id item-id
          :change_at (:change_at full-item)
          :ttl (:ttl full-item)})))))

(schema/defn ^:always-validate retrieve :- [{:container-id UniqueDraftID :item-id UniqueDraftID :change-at lib-schema/ISO8601}]
  [container :- (schema/conditional sequential? [UniqueDraftID] :else UniqueDraftID)]
  (if (sequential? container)
    (flatten (pmap retrieve container))
    (->> (far/query c/dynamodb-opts table-name {:container_id [:eq container]}
          {:filter-expr "#k > :v"
           :expr-attr-names {"#k" "ttl"}
           :expr-attr-vals {":v" (ttl/ttl-now)}})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :change_at :change-at}))
      (map #(select-keys % [:container-id :item-id :change-at])))))

(comment

  (require '[oc.lib.time :as oc-time])
  (require '[oc.change.resources.change :as change] :reload)

  (far/list-tables c/dynamodb-opts)

  (far/delete-table c/dynamodb-opts change/table-name)
  (aprint
    (far/create-table c/dynamodb-opts
      change/table-name
      [:container_id :s]
      {:range-keydef [:item_id :s]
       :throughput {:read 1 :write 1}
       :block? true}))

  ;; GSI used for delete via container-id
  (aprint
    @(far/update-table config/dynamodb-opts
      change/table-name
      {:gsindexes {:operation :create
                   :name change/container-id-item-id-gsi-name
                   :throughput {:read 1 :write 1}
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