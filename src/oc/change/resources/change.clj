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

(schema/defn ^:always-validate retrieve :- [{:container-id lib-schema/UniqueID :item-id lib-schema/UniqueID :change-at lib-schema/ISO8601}]
  [container :- (schema/conditional sequential? [lib-schema/UniqueID] :else lib-schema/UniqueID)]
  (if (sequential? container)
    (flatten (pmap retrieve container))
    (->> (far/query c/dynamodb-opts table-name {:container_id [:eq container]})
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

  (aprint (far/describe-table c/dynamodb-opts change/table-name))

  (change/store! "1111-1111-1111" "2222-2222-2222" (oc-time/current-timestamp))
  (change/store! "1111-1111-1111" "3333-3333-3333" (oc-time/current-timestamp))
  (change/retrieve "1111-1111-1111")

  (change/store! "4444-4444-4444" "5555-5555-5555" (oc-time/current-timestamp))
  (change/retrieve ["1111-1111-1111" "4444-4444-4444"])

)