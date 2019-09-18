(ns oc.change.resources.read
  "Store records of users reading an item, and provide retrieval and counts of the same."
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [taoensso.timbre :as timbre]
            [oc.change.config :as c]))

(def table-name (keyword (str c/dynamodb-table-prefix "_read")))
(def user-id-gsi-name (str c/dynamodb-table-prefix "_read_gsi_user_id"))
(def container-id-item-id-gsi-name (str c/dynamodb-table-prefix "_read_gsi_container_id_item_id"))
(def container-id-gsi-name (str c/dynamodb-table-prefix "_read_gsi_container_id"))

;; In theory, DynamoDB (and by extension, Faraday) support `{:return :count}` but it doesn't seem to be working
;; https://github.com/ptaoussanis/faraday/issues/91
(defn- count-for [user-id item-id]
  (let [results (far/query c/dynamodb-opts table-name {:item_id [:eq item-id]})]
    {:item-id item-id :count (count results) :last-read-at (:read_at (last (sort-by :read-at (filterv #(= (:user_id %) user-id) results))))}))

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

(schema/defn ^:always-validate delete-by-item!
  [container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (doseq [item (far/query c/dynamodb-opts table-name {:item_id [:eq item-id]} {:index container-id-item-id-gsi-name})]
    (far/delete-item c/dynamodb-opts table-name {:item_id (:item_id item)
                                                 :user_id (:user_id item)})))

(schema/defn ^:always-validate delete-by-container!
  [container-id :- lib-schema/UniqueID]
  (doseq [item (far/query c/dynamodb-opts table-name {:container_id [:eq container-id]} {:index container-id-gsi-name})]
    (far/delete-item c/dynamodb-opts table-name {:item_id (:item_id item)
                                                 :user_id (:user_id item)})))

(schema/defn ^:always-validate move-item!
  [item-id :- lib-schema/UniqueID old-container-id :- lib-schema/UniqueID new-container-id :- lib-schema/UniqueID]
  (let [items-to-move (far/query c/dynamodb-opts table-name {:item_id [:eq item-id]}
                       {:index container-id-item-id-gsi-name})]
    (timbre/info "Read move-item! for" item-id "moving:" (count items-to-move) "items from container" old-container-id "to" new-container-id)
    (doseq [item items-to-move]
      (let [full-item (far/get-item c/dynamodb-opts table-name {:item_id (:item_id item) :user_id (:user_id item)})]
        (far/delete-item c/dynamodb-opts table-name {:item_id (:item_id full-item)
                                                     :user_id (:user_id full-item)})
        (far/put-item c/dynamodb-opts table-name {
          :org-id (:org-id full-item)
          :container_id new-container-id
          :item_id (:item_id full-item)
          :user_id (:user_id full-item)
          :name (:name full-item)
          :avatar_url (:avatar_url full-item)
          :read_at (:read_at full-item)})))))

(schema/defn ^:always-validate retrieve-by-item :- [{:user-id lib-schema/UniqueID
                                                     :name schema/Str
                                                     :avatar-url (schema/maybe schema/Str)
                                                     :read-at lib-schema/ISO8601}]
  [item-id :- lib-schema/UniqueID]
  (->> (far/query c/dynamodb-opts table-name {:item_id [:eq item-id]})
      (map #(clojure.set/rename-keys % {:user_id :user-id :avatar_url :avatar-url :read_at :read-at}))
      (map #(select-keys % [:user-id :name :avatar-url :read-at]))))

(schema/defn ^:always-validate retrieve-by-user :- [{(schema/optional-key :container-id) lib-schema/UniqueID
                                                     :item-id lib-schema/UniqueID
                                                     :read-at lib-schema/ISO8601}]
  ([user-id :- lib-schema/UniqueID]
  (->>
      (far/query c/dynamodb-opts table-name {:user_id [:eq user-id]} {:index user-id-gsi-name})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:container-id :item-id :read-at]))))

  ([user-id :- lib-schema/UniqueID container-id :- lib-schema/UniqueID]
  (->>
      (far/query c/dynamodb-opts table-name {:user_id [:eq user-id] :container_id [:eq container-id]}
                                            {:index user-id-gsi-name})
      (map #(clojure.set/rename-keys % {:item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:item-id :read-at])))))


(schema/defn ^:always-validate counts :- [{:item-id lib-schema/UniqueID
                                           :count schema/Int
                                           :last-read-at (schema/maybe lib-schema/ISO8601)}]
  [item-ids :- [lib-schema/UniqueID] user-id :- lib-schema/UniqueID]
  (pmap (partial count-for user-id) item-ids))

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

  ;; Add GSI for delete all via item-id
  (aprint
    @(far/update-table config/dynamodb-opts
      read/table-name
      {:gsindexes {:operation :create
                   :name read/container-id-item-id-gsi-name
                   :throughput {:read 1 :write 1}
                   :hash-keydef [:item_id :s]
                   :range-keydef [:container_id :s]
                   :projection :keys-only}}))

  (doseq [item (far/query config/dynamodb-opts read/table-name {:item_id [:eq "512b-4ad1-9924"]} {:index read/container-id-item-id-gsi-name})]
    (aprint
      (far/delete-item config/dynamodb-opts read/table-name {:item_id (:item_id item)
                                                             :user_id (:user_id item)})))

  ;; Add GSI for delete all via container-id
  (aprint @(far/update-table config/dynamodb-opts read/table-name
            {:gsindexes
              {:operation :create
               :name read/container-id-gsi-name
               :throughput {:read 1 :write 1}
               :hash-keydef [:container_id :s]
               :range-keydef [:user_id :s]
               :projection :keys-only}}))

  (doseq [item (far/query config/dynamodb-opts read/table-name {:container_id [:eq "25a3-4692-bf02"]} {:index read/container-id-gsi-name})]
    (aprint
      (far/delete-item config/dynamodb-opts read/table-name {:item_id (:item_id item)
                                                             :user_id (:user_id item)})))

  (far/update-table config/dynamodb-opts read/table-name {:gsindexes {:operation :delete :name read/container-id-gsi-name}})

  (aprint (far/describe-table config/dynamodb-opts read/table-name))

  (read/store! "1111-1111-1111" "cccc-cccc-cccc" "eeee-eeee-eeee" "aaaa-aaaa-aaaa"
               "Albert Camus" "http//..." (oc-time/current-timestamp))

  (read/retrieve-by-item "eeee-eeee-eeee")
  (read/retrieve-by-user "aaaa-aaaa-aaaa")
  (read/retrieve-by-user "aaaa-aaaa-aaaa" "cccc-cccc-cccc")

  (read/store! "1111-1111-1111" "cccc-cccc-cccc" "eeee-eeee-eeee" "bbbb-bbbb-bbbb"
               "Arthur Schopenhauer" "http//..." (oc-time/current-timestamp))

  (read/retrieve-by-item "eeee-eeee-eeee")
  (read/retrieve-by-user "aaaa-aaaa-aaaa")
  (read/retrieve-by-user "aaaa-aaaa-aaaa" "eeee-eeee-eeee")

  (read/store! "1111-1111-1111" "cccc-cccc-cccc" "eeee-eeee-eee1" "aaaa-aaaa-aaaa"
               "Albert Camus" "http//..." (oc-time/current-timestamp))

  (read/counts ["eeee-eeee-eeee" "eeee-eeee-eee1"] "1234-1234-1234")

  (far/delete-table c/dynamodb-opts read/table-name)
)
