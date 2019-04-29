(ns oc.change.resources.read
  "Store records of users reading an item, and provide retrieval and counts of the same."
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]))

(def table-name (keyword (str c/dynamodb-table-prefix "_read")))
(def user-id-gsi-name (str c/dynamodb-table-prefix "_read_gsi_user_id"))

;; In theory, DynamoDB (and by extension, Faraday) support `{:return :count}` but it doesn't seem to be working
;; https://github.com/ptaoussanis/faraday/issues/91
;; `{return [:none]}` is simply a way to get an empty map for every item, then we count the empty maps
(defn- count-for [item-id]
  (let [results (far/query c/dynamodb-opts table-name {:item_id [:eq item-id]} {:return [:none]})]
    {:item-id item-id :count (count results)}))

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

(schema/defn ^:always-validate delete!
  [item-id :- lib-schema/UniqueID user-id :- lib-schema/UniqueID]
  (far/delete-item c/dynamodb-opts table-name {:item_id item-id
                                                        :user_id user-id}))

(schema/defn ^:always-validate retrieve-by-item :- [{:user-id lib-schema/UniqueID
                                                     :name schema/Str
                                                     :avatar-url (schema/maybe schema/Str)
                                                     :read-at lib-schema/ISO8601}]
  [item-id :- lib-schema/UniqueID]
  (->> (far/query c/dynamodb-opts table-name {:item_id [:eq item-id]})
      (map #(clojure.set/rename-keys % {:user_id :user-id :avatar_url :avatar-url :read_at :read-at}))
      (map #(select-keys % [:user-id :name :avatar-url :read-at]))))

(schema/defn ^:always-validate retrieve-by-user :- [{:container-id lib-schema/UniqueID
                                                     :item-id lib-schema/UniqueID
                                                     :read-at lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID]
  (->> 
      (far/query c/dynamodb-opts table-name {:user_id [:eq user-id]} {:index user-id-gsi-name})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:container-id :item-id :read-at]))))

(schema/defn ^:always-validate counts :- [{:item-id lib-schema/UniqueID
                                           :count schema/Int}]
  [item-ids :- [lib-schema/UniqueID]]
  (pmap count-for item-ids))

(comment

  (require '[oc.lib.time :as oc-time])
  (require '[oc.change.resources.read :as read] :reload)

  (far/list-tables config/dynamodb-opts)

  (far/delete-table config/dynamodb-opts read/table-name)
  (aprint
    (far/create-table config/dynamodb-opts
      read/table-name
      [:item_id :s]
      {:range-keydef [:user_id :s]
       :throughput {:read 1 :write 1}
       :block? true}))
  (aprint 
    (far/update-table config/dynamodb-opts
      read/table-name
      {:gsindexes {:operation :create
                   :name read/user-id-gsi-name
                   :throughput {:read 1 :write 1}
                   :hash-keydef [:user_id :s]
                   :range-keydef [:container_id :s]
                   :projection :all}}))

  (aprint (far/describe-table config/dynamodb-opts read/table-name))

  (read/store! "1111-1111-1111" "cccc-cccc-cccc" "eeee-eeee-eeee" "aaaa-aaaa-aaaa"
               "Albert Camus" "http//..." (oc-time/current-timestamp))

  (read/retrieve-by-item "eeee-eeee-eeee")
  (read/retrieve-by-user "aaaa-aaaa-aaaa")

  (read/store! "1111-1111-1111" "cccc-cccc-cccc" "eeee-eeee-eeee" "bbbb-bbbb-bbbb"
               "Arthur Schopenhauer" "http//..." (oc-time/current-timestamp))

  (read/retrieve-by-item "eeee-eeee-eeee")
  (read/retrieve-by-user "aaaa-aaaa-aaaa")

  (read/store! "1111-1111-1111" "cccc-cccc-cccc" "eeee-eeee-eee1" "aaaa-aaaa-aaaa"
               "Albert Camus" "http//..." (oc-time/current-timestamp))
  
  (read/counts ["eeee-eeee-eeee" "eeee-eeee-eee1"])

  (far/delete-table c/dynamodb-opts read/table-name)
)