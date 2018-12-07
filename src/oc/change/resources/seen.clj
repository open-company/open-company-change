(ns oc.change.resources.seen
  "Store tuples of: user-id, container-id, item-id and timestamp, with a TTL"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]
            [oc.change.util.ttl :as ttl-util]))

(def entire-container "9999-9999-9999")

(def table-name (keyword (str c/dynamodb-table-prefix "_seen")))

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
      :container_item_id (str container-id "-" item-id)
      :container_id container-id
      :item-id item-id
      :user-id user-id
      :seen_at seen-at
      :ttl (ttl-util/ttl-epoch c/seen-ttl)})
  true))

(schema/defn ^:always-validate retrieve :- [{:container-id lib-schema/UniqueID :item-id lib-schema/UniqueID :seen-at lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID]
  (->> (far/query c/dynamodb-opts table-name {:user_id [:eq user-id]}
        {:filter-expr "#k > :v"
         :expr-attr-names {"#k" "ttl"}
         :expr-attr-vals {":v" (ttl-util/ttl-now)}})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :seen_at :seen-at}))
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
       :throughput {:read 1 :write 1}
       :block? true}))

  (aprint (far/describe-table c/dynamodb-opts seen/table-name))

  (seen/store! "abcd-1234-abcd" "5678-edcb-5678" (oc-time/current-timestamp))

  (seen/retrieve "abcd-1234-abcd")

  (seen/store! "abcd-1234-abcd" "1ab1-2ab2-3ab3" "1111-1111-1111" (oc-time/current-timestamp))

  (seen/retrieve "abcd-1234-abcd")
  
  (far/delete-table c/dynamodb-opts seen/table-name)
)