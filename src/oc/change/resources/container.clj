(ns oc.change.resources.container
  "Store tuples of: container-id and timestamp"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]))

(def table-name (keyword (str c/dynamodb-table-prefix "_container_time")))

(schema/defn ^:always-validate change!
  [container-id :- lib-schema/UniqueID change-at :- lib-schema/ISO8601]
  (far/put-item c/dynamodb-opts table-name {
      :container_id container-id
      :change_at change-at
      :ttl (coerce/to-long (time/plus (time/now) (time/days c/container-time-ttl)))})
  true)

(schema/defn ^:always-validate change :- [{:container-id lib-schema/UniqueID :change-at lib-schema/ISO8601}]
  [container-ids :- [lib-schema/UniqueID]]
  (->> (far/batch-get-item c/dynamodb-opts {table-name {
          :prim-kvs (map #(hash-map :container_id %) container-ids)
          :attrs [:container_id :change_at]}})
    :container_time
    (map #(clojure.set/rename-keys % {:container_id :container-id :change_at :change-at}))))

(comment

  (require '[oc.lib.time :as oc-time])
  (require '[oc.change.resources.container :as container] :reload)

  (far/list-tables c/dynamodb-opts)

  (far/delete-table c/dynamodb-opts container/table-name)
  (aprint 
    (far/create-table c/dynamodb-opts
      container/table-name
      [:container_id :s]
      {:throughput {:read 1 :write 1}
       :block? true}))

  (aprint (far/describe-table  c/dynamodb-opts container/table-name))

  (container/change! "1111-1111-1111" (oc-time/current-timestamp))

  (container/change ["5678-edcb-5678"])

  (container/change! "5678-edcb-5678" (oc-time/current-timestamp))
  (container/change! "1ab1-2ab2-3ab3" (oc-time/current-timestamp))

  (u/change ["5678-edcb-5678" "1ab1-2ab2-3ab3" "1111-1111-1111"])

)