(ns oc.change.resources.change
  "Store tuples of: container-id, item-id and change timestamp, with a TTL"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]))

(def table-name (keyword (str c/dynamodb-table-prefix "_change")))

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
  (let [ttl-date (time/plus (time/now) (time/days c/change-ttl))]
    (far/put-item c/dynamodb-opts table-name {
        :container_id container-id
        :item_id item-id
        :change_at change-at
        :ttl (coerce/to-epoch ttl-date)}))
  true)

(schema/defn ^:always-validate retrieve :- [{:container-id UniqueDraftID :item-id UniqueDraftID :change-at lib-schema/ISO8601}]
  [container :- (schema/conditional sequential? [UniqueDraftID] :else UniqueDraftID)]
  (if (sequential? container)
    (flatten (pmap retrieve container))
    (let [now (coerce/to-epoch (time/now))]
      (->> (far/query c/dynamodb-opts table-name {:container_id [:eq container]}
            {:filter-expr "#k > :v"
             :expr-attr-names {"#k" "ttl"}
             :expr-attr-vals {":v" now}})
        (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :change_at :change-at}))
        (map #(select-keys % [:container-id :item-id :change-at]))))))

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