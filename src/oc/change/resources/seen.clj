(ns oc.change.resources.seen
  "Store triples of: user-id, container-id and timestamp"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]))

(def table-name (keyword (str c/dynamodb-table-prefix "_seen")))

(schema/defn ^:always-validate store!
  [user-id :- lib-schema/UniqueID container-id :- lib-schema/UniqueID seen-at :- lib-schema/ISO8601]
  (far/put-item c/dynamodb-opts table-name {
      :user_id user-id
      :container_id container-id
      :seen_at seen-at
      :ttl (coerce/to-long (time/plus (time/now) (time/days c/seen-ttl)))})
  true)

(schema/defn ^:always-validate retrieve :- [{:container-id lib-schema/UniqueID :seen-at lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID container-ids :- [lib-schema/UniqueID]]
  (if (empty? container-ids)
    []
    (->> (far/batch-get-item c/dynamodb-opts {table-name {
            :prim-kvs (map #(hash-map :user_id user-id :container_id %) container-ids)
            :attrs [:container_id :seen_at]}})
      table-name
      (map #(clojure.set/rename-keys % {:container_id :container-id :seen_at :seen-at})))))

(comment

  (require '[oc.lib.time :as oc-time])
  (require '[oc.change.resources.seen :as seen] :reload)

  (far/list-tables c/dynamodb-opts)

  (far/delete-table c/dynamodb-opts seen/table-name)
  (aprint
    (far/create-table c/dynamodb-opts
      seen/table-name
      [:user_id :s]
      {:range-keydef [:container_id :s]
       :throughput {:read 1 :write 1}
       :block? true}))

  (aprint (far/describe-table c/dynamodb-opts seen/table-name))

  (seen/store! "abcd-1234-abcd" "5678-edcb-5678" (oc-time/current-timestamp))

  (seen/retrieve "abcd-1234-abcd" ["5678-edcb-5678"])

  (seen/store! "abcd-1234-abcd" "1ab1-2ab2-3ab3" (oc-time/current-timestamp))

  (seen/store! "abcd-1234-abcd" ["5678-edcb-5678" "1ab1-2ab2-3ab3" "1111-1111-1111"])

  (far/delete-table c/dynamodb-opts seen/table-name)
)