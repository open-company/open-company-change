(ns oc.change.resources.read
  ""
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]))

(def table-name (keyword (str c/dynamodb-table-prefix "_read")))

(schema/defn ^:always-validate store!
  
  ;; Store a read entry for the specified user
  ([org-id :-  lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    item-id :- lib-schema/UniqueID
    user-id :- lib-schema/UniqueID
    user-name :- schema/Str
    avatar-url :- (schema/maybe schema/Str)
    read-at :- lib-schema/ISO8601]
  (far/put-item c/dynamodb-opts table-name {
      :org-id org-id
      :container_id container-id
      :item_id item-id
      :user_id user-id
      :name user-name
      :avatar_url avatar-url
      :read_at read-at})
  true))

(schema/defn ^:always-validate retrieve :- [{:user-id lib-schema/UniqueID
                                             :name schema/Str
                                             :avatar-url (schema/maybe schema/Str)
                                             :read-at lib-schema/ISO8601}]
  [item-id :- lib-schema/UniqueID]
  (->> (far/query c/dynamodb-opts table-name {:item_id [:eq item-id]})
      (map #(clojure.set/rename-keys % {:user_id :user-id :avatar_url :avatar-url :read_at :read-at}))
      (map #(select-keys % [:user-id :name :avatar-url :read-at]))))

(comment

  (require '[oc.lib.time :as oc-time])
  (require '[oc.change.resources.read :as read] :reload)

  (far/list-tables c/dynamodb-opts)

  (far/delete-table c/dynamodb-opts read/table-name)
  (aprint
    (far/create-table c/dynamodb-opts
      read/table-name
      [:item_id :s]
      {:range-keydef [:user_id :s]
       :throughput {:read 1 :write 1}
       :block? true}))

  (aprint (far/describe-table c/dynamodb-opts read/table-name))

  (read/store! "1111-1111-1111" "cccc-cccc-cccc" "eeee-eeee-eeee" "aaaa-aaaa-aaaa"
               "Albert Camus" "http//..." (oc-time/current-timestamp))

  (read/retrieve "eeee-eeee-eeee")

  (read/store! "1111-1111-1111" "cccc-cccc-cccc" "eeee-eeee-eeee" "bbbb-bbbb-bbbb"
               "Arthur Schopenhauer" "http//..." (oc-time/current-timestamp))

  (read/retrieve "eeee-eeee-eeee")
  
  (far/delete-table c/dynamodb-opts read/table-name)
)