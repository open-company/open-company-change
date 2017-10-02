(ns oc.change.resources.user
  "Store triples of: user-id, container-id and timestamp"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]))

(def table-name (keyword (str c/dynamodb-table-prefix "_user_container_time")))

(schema/defn ^:always-validate seen!
  [user-id :- lib-schema/UniqueID container-id :- lib-schema/UniqueID seen-at :- lib-schema/ISO8601]
  (far/put-item c/dynamodb-opts table-name {
      :user_id user-id
      :container_id container-id
      :seen_at seen-at})
  true)

(schema/defn ^:always-validate seen :- [{:container-id lib-schema/UniqueID :seen-at lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID container-ids :- [lib-schema/UniqueID]]
  (->> (far/batch-get-item c/dynamodb-opts {table-name {
          :prim-kvs (map #(hash-map :user_id user-id :container_id %) container-ids)
          :attrs [:container_id :seen_at]}})
    :user_container_time
    (map #(clojure.set/rename-keys % {:container_id :container-id :seen_at :seen-at}))))

(comment

  (require '[oc.lib.time :as oc-time])
  (require '[oc.change.resources.user :as u] :reload)

  (far/list-tables c/dynamodb-opts)

  (far/delete-table c/dynamodb-opts u/table-name)
  (aprint 
    (far/create-table c/dynamodb-opts
      u/table-name
      [:user_id :s]
      {:range-keydef [:container_id :s]
       :throughput {:read 1 :write 1}
       :block? true}))

  (aprint (far/describe-table c/dynamodb-opts u/table-name))

  (u/seen! "abcd-1234-abcd" "5678-edcb-5678" (oc-time/current-timestamp))

  (u/seen "abcd-1234-abcd" ["5678-edcb-5678"])

  (u/seen "abcd-1234-abcd" "1ab1-2ab2-3ab3" (oc-time/current-timestamp))

  (u/seen! "abcd-1234-abcd" ["5678-edcb-5678" "1ab1-2ab2-3ab3" "1111-1111-1111"])

  (far/delete-table c/dynamodb-opts u/table-name)
)