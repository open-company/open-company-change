(ns oc.change.resources.seen
  "Store tuples of: user-id, container-id, item-id and timestamp, with a TTL"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [taoensso.timbre :as timbre]
            [oc.change.config :as c]
            [oc.lib.dynamo.common :as ttl]))

(def entire-container "9999-9999-9999")

(def table-name (keyword (str c/dynamodb-table-prefix "_seen")))

(def container-id-item-id-gsi-name (str c/dynamodb-table-prefix "_seen_gsi_container_id_item_id"))
(def container-id-gsi-name (str c/dynamodb-table-prefix "_seen_gsi_container_id"))

(defn- create-container-item-id [container-id item-id]
  (str container-id "-" item-id))

(schema/defn ^:always-validate store!
  
  ;; Saw the whole container, so the item-id is a placeholder
  ([user-id :- lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    seen-at :- lib-schema/ISO8601]
  (store! user-id container-id entire-container seen-at))

  ;; Store a seen entry for the specified user
  ([user-id :- lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    item-id :- lib-schema/UniqueID
    seen-at :- lib-schema/ISO8601]
  (far/put-item c/dynamodb-opts table-name {
      :user_id user-id
      :container_item_id (create-container-item-id container-id item-id)
      :container_id container-id
      :item-id item-id
      :user-id user-id
      :seen_at seen-at
      :ttl (ttl/ttl-epoch c/seen-ttl)})
  true))

(schema/defn ^:always-validate delete-by-item!
  [container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (doseq [item (far/query c/dynamodb-opts table-name {:item-id [:eq item-id]} {:index container-id-item-id-gsi-name})]
    (far/delete-item c/dynamodb-opts table-name {:container_item_id (:container_item_id item)
                                                 :user_id (:user_id item)})))

(schema/defn ^:always-validate delete-by-container!
  [container-id :- lib-schema/UniqueID]
  (doseq [item (far/query c/dynamodb-opts table-name {:container_id [:eq container-id]} {:index container-id-gsi-name})]
    (far/delete-item c/dynamodb-opts table-name {:container_item_id (:container_item_id item)
                                                 :user_id (:user_id item)})))

(schema/defn ^:always-validate move-item!
  [item-id :- lib-schema/UniqueID old-container-id :- lib-schema/UniqueID new-container-id :- lib-schema/UniqueID]
  (let [items-to-move (far/query c/dynamodb-opts table-name {:item-id [:eq item-id] :container_id [:eq old-container-id]}
                       {:index container-id-item-id-gsi-name})]
    (timbre/info "Seen move-item! for" item-id "moving:" (count items-to-move) "items from container" old-container-id "to" new-container-id)
    (doseq [item items-to-move]
      (let [old-container-item-id (create-container-item-id old-container-id item-id)
            new-container-item-id (create-container-item-id new-container-id item-id)
            full-item (far/get-item c/dynamodb-opts table-name {:user_id (:user_id item) :container_item_id old-container-item-id})]

        (far/delete-item c/dynamodb-opts table-name {:container_item_id (:container_item_id full-item)
                                                     :user_id (:user_id full-item)})
        (far/put-item c/dynamodb-opts table-name {
          :user_id (:user_id full-item)
          :container_item_id new-container-item-id
          :container_id (:container_id full-item)
          :item-id (:item-id full-item)
          :user-id (:user-id full-item)
          :seen_at (:seen_at full-item)
          :ttl (:ttl full-item)})))))

(schema/defn ^:always-validate retrieve :- [{:container-id lib-schema/UniqueID :item-id lib-schema/UniqueID :seen-at lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID]
  (->> (far/query c/dynamodb-opts table-name {:user_id [:eq user-id]}
        {:filter-expr "#k > :v"
         :expr-attr-names {"#k" "ttl"}
         :expr-attr-vals {":v" (ttl/ttl-now)}})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item-id :item-id :seen_at :seen-at}))
      (map #(select-keys % [:container-id :item-id :seen-at]))))

(comment

  (require '[oc.lib.time :as oc-time])
  (require '[oc.change.resources.seen :as seen] :reload)

  (far/list-tables c/dynamodb-opts)

  (far/delete-table c/dynamodb-opts seen/table-name)
  (aprint
    (far/create-table c/dynamodb-opts
      seen/table-name
      [:user_id :s]
      {:range-keydef [:container_item_id :s]
       :billing-mode :pay-per-request
       :block? true}))
  ;; GSI used for delete via item-id
  (aprint
    @(far/update-table config/dynamodb-opts
      seen/table-name
      {:gsindexes {:operation :create
                   :name seen/container-id-item-id-gsi-name
                   :hash-keydef [:item-id :s]
                   :range-keydef [:container_id :s]
                   :projection :keys-only}}))

  (doseq [item (far/query config/dynamodb-opts seen/table-name {:item-id [:eq "512b-4ad1-9924"]} {:index seen/container-id-item-id-gsi-name})]
    (aprint
      (far/delete-item config/dynamodb-opts seen/table-name {:container_item_id (:container_item_id item)
                                                             :user_id (:user_id item)})))

  ;; GSI used for delete via container-id
  (aprint
    @(far/update-table config/dynamodb-opts
      seen/table-name
      {:gsindexes {:operation :create
                   :name seen/container-id-gsi-name
                   :hash-keydef [:container_id :s]
                   :range-keydef [:user_id :s]
                   :projection :keys-only}}))

  (doseq [item (far/query config/dynamodb-opts seen/table-name {:container_id [:eq "25a3-4692-bf02"]} {:index seen/container-id-gsi-name})]
    (aprint
      (far/delete-item config/dynamodb-opts seen/table-name {:container_item_id (:container_item_id item)
                                                             :user_id (:user_id item)})))

  (far/delete-item config/dynamodb-opts seen/table-name {:container_item_id "25a3-4692-bf02-512b-4ad1-9924"})

  (aprint (far/describe-table config/dynamodb-opts seen/table-name))

  (seen/store! "abcd-1234-abcd" "5678-edcb-5678" (oc-time/current-timestamp))

  (seen/retrieve "abcd-1234-abcd")

  (seen/store! "abcd-1234-abcd" "1ab1-2ab2-3ab3" "1111-1111-1111" (oc-time/current-timestamp))

  (seen/retrieve "abcd-1234-abcd")
  
  (far/delete-table c/dynamodb-opts seen/table-name)
)