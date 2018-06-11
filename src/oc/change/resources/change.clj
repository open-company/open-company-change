(ns oc.change.resources.change
  "Store tuples of: container-id, item-id and change timestamp"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]))

(def table-name (keyword (str c/dynamodb-table-prefix "_change")))

(schema/defn ^:always-validate store!
  [container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID change-at :- lib-schema/ISO8601]
  (far/put-item c/dynamodb-opts table-name {
      :container_id container-id
      :item_id item-id
      :change_at change-at
      :ttl (coerce/to-long (time/plus (time/now) (time/days c/change-ttl)))})
  true)

(schema/defn ^:always-validate retrieve :- [{:container-id lib-schema/UniqueID :change-at lib-schema/ISO8601}]
  [container-ids :- [lib-schema/UniqueID]]
  (if (empty? container-ids)
    []
    (->> (far/batch-get-item c/dynamodb-opts {table-name {
            :prim-kvs (map #(hash-map :container_id %) container-ids)
            :attrs [:container_id :item_id :change_at]}})
      table-name
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :change_at :change-at})))))

(comment

  (require '[oc.lib.time :as oc-time])
  (require '[oc.change.resources.change :as change] :reload)

  (far/list-tables c/dynamodb-opts)

  (far/delete-table c/dynamodb-opts container/table-name)
  (aprint
    (far/create-table c/dynamodb-opts
      container/table-name
      [:container_id :s]
      {:throughput {:read 1 :write 1}
       :block? true}))

  (aprint (far/describe-table  c/dynamodb-opts container/table-name))

  (change/store! "1111-1111-1111" "2222-2222-2222" (oc-time/current-timestamp))
  (change/store! "1111-1111-1111" "3333-3333-3333" (oc-time/current-timestamp))

  (change/retrieve ["1111-1111-1111"])

  (change/store! "1ab1-2ab2-3ab3" (oc-time/current-timestamp))

  ;(u/change ["1111-1111-1111" "1ab1-2ab2-3ab3" "5678-edcb-5678"])

)