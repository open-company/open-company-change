(ns oc.change.resources.read
  "Store records of users reading an item, and provide retrieval and counts of the same."
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [taoensso.timbre :as timbre]
            [oc.change.config :as c]
            [oc.lib.change.resources.read :as lib-read]))

;; Table and GSIndex names

(def table-name (lib-read/table-name c/dynamodb-opts))
(def part-table-name (lib-read/part-table-name c/dynamodb-opts))
(def part-item-id-gsi-name (lib-read/part-item-id-gsi-name c/dynamodb-opts))
(def user-id-gsi-name (lib-read/user-id-gsi-name c/dynamodb-opts))
(def part-user-id-gsi-name (lib-read/part-user-id-gsi-name c/dynamodb-opts))
(def org-id-user-id-gsi-name (lib-read/org-id-user-id-gsi-name c/dynamodb-opts))
(def part-org-id-user-id-gsi-name (lib-read/part-org-id-user-id-gsi-name c/dynamodb-opts))
(def container-id-item-id-gsi-name (lib-read/container-id-item-id-gsi-name c/dynamodb-opts))
(def part-container-id-item-id-gsi-name (lib-read/part-container-id-item-id-gsi-name c/dynamodb-opts))
(def container-id-gsi-name (lib-read/container-id-gsi-name c/dynamodb-opts))
(def part-container-id-gsi-name (lib-read/part-container-id-gsi-name c/dynamodb-opts))
(def part-user-id-item-id-gsi-name (lib-read/part-user-id-item-id-gsi-name c/dynamodb-opts))

;; Store

(schema/defn ^:always-validate store!
  ;; Store a read entry fgr a user and an item
  ([org-id :-  lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    item-id :- lib-schema/UniqueID
    user-id :- lib-schema/UniqueID
    user-name :- schema/Str
    avatar-url :- (schema/maybe schema/Str)
    read-at :- lib-schema/ISO8601]
  (lib-read/store! c/dynamodb-opts org-id container-id item-id user-id user-name avatar-url read-at)))

(schema/defn ^:always-validate store-part!
  ;; Store a read entry fgr a user and a part
  ([org-id :-  lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    item-id :- lib-schema/UniqueID
    part-id :- lib-schema/UniqueID
    user-id :- lib-schema/UniqueID
    user-name :- schema/Str
    avatar-url :- (schema/maybe schema/Str)
    read-at :- lib-schema/ISO8601]
  (lib-read/store-part! c/dynamodb-opts org-id container-id item-id part-id user-id user-name avatar-url read-at)))

;; Retrieve

(schema/defn ^:always-validate retrieve-by-part :- [{:user-id lib-schema/UniqueID
                                                     :name schema/Str
                                                     :avatar-url (schema/maybe schema/Str)
                                                     :read-at lib-schema/ISO8601}]
  [part-id :- lib-schema/UniqueID]
  (lib-read/retrieve-by-part c/dynamodb-opts part-id))

(schema/defn ^:always-validate retrieve-by-item :- [{:user-id lib-schema/UniqueID
                                                     :name schema/Str
                                                     :avatar-url (schema/maybe schema/Str)
                                                     :read-at lib-schema/ISO8601}]
  [item-id :- lib-schema/UniqueID]
  (lib-read/retrieve-by-item c/dynamodb-opts item-id))

(schema/defn ^:always-validate retrieve-parts-by-item :- [{:user-id lib-schema/UniqueID
                                                           :item-id lib-schema/UniqueID
                                                           :part-id lib-schema/UniqueID}]
  [item-id :- lib-schema/UniqueID]
  (lib-read/retrieve-parts-by-item c/dynamodb-opts item-id))

(schema/defn ^:always-validate retrieve-by-user :- [{(schema/optional-key :container-id) lib-schema/UniqueID
                                                     :item-id lib-schema/UniqueID
                                                     :read-at lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID]
  (lib-read/retrieve-by-user c/dynamodb-opts user-id))

(schema/defn ^:always-validate retrieve-parts-by-user :- [{(schema/optional-key :container-id) lib-schema/UniqueID
                                                           :item-id lib-schema/UniqueID
                                                           :part-id lib-schema/UniqueID
                                                           :read-at lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID]
   (lib-read/retrieve-parts-by-user c/dynamodb-opts user-id))

(schema/defn ^:always-validate retrieve-by-user-part :- {(schema/optional-key :org-id) lib-schema/UniqueID
                                                         (schema/optional-key :container-id) lib-schema/UniqueID
                                                         (schema/optional-key :item-id) lib-schema/UniqueID
                                                         (schema/optional-key :oart-id) lib-schema/UniqueID
                                                         (schema/optional-key :user-id) lib-schema/UniqueID
                                                         (schema/optional-key :name) schema/Str
                                                         (schema/optional-key :avatar-url) (schema/maybe schema/Str)
                                                         (schema/optional-key :read-at) lib-schema/ISO8601}
  [user-id :- lib-schema/UniqueID part-id :- lib-schema/UniqueID]
  (lib-read/retrieve-by-user-part c/dynamodb-opts user-id part-id))

(schema/defn ^:always-validate retrieve-by-user-item :- {(schema/optional-key :user-id) lib-schema/UniqueID
                                                         (schema/optional-key :name) schema/Str
                                                         (schema/optional-key :avatar-url) (schema/maybe schema/Str)
                                                         (schema/optional-key :read-at) lib-schema/ISO8601}
  [user-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (lib-read/retrieve-by-user-item c/dynamodb-opts user-id item-id))

(schema/defn ^:always-validate retrieve-parts-by-user-item :- [{(schema/optional-key :part-id) lib-schema/UniqueID
                                                                (schema/optional-key :user-id) lib-schema/UniqueID
                                                                (schema/optional-key :name) schema/Str
                                                                (schema/optional-key :avatar-url) (schema/maybe schema/Str)
                                                                (schema/optional-key :read-at) lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (lib-read/retrieve-parts-by-user-item c/dynamodb-opts user-id item-id))

(schema/defn ^:always-validate retrieve-by-user-container :- [{:item-id lib-schema/UniqueID
                                                               :read-at lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID container-id :- lib-schema/UniqueID]
  (lib-read/retrieve-by-user-container c/dynamodb-opts user-id container-id))

(schema/defn ^:always-validate retrieve-parts-by-user-container :- [{:item-id lib-schema/UniqueID
                                                                     :part-id lib-schema/UniqueID
                                                                     :read-at lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID container-id :- lib-schema/UniqueID]
  (lib-read/retrieve-parts-by-user-container c/dynamodb-opts user-id container-id))

(schema/defn ^:always-validate retrieve-by-user-org :- [{(schema/optional-key :item-id) lib-schema/UniqueID
                                                         (schema/optional-key :container-id) lib-schema/UniqueID
                                                         (schema/optional-key :read-at) lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID]
  (lib-read/retrieve-by-user-org c/dynamodb-opts user-id org-id))

(schema/defn ^:always-validate retrieve-parts-by-user-org :- [{(schema/optional-key :part-id) lib-schema/UniqueID
                                                               (schema/optional-key :item-id) lib-schema/UniqueID
                                                               (schema/optional-key :container-id) lib-schema/UniqueID
                                                               (schema/optional-key :read-at) lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID]
  (lib-read/retrieve-parts-by-user-org c/dynamodb-opts user-id org-id))

(schema/defn ^:always-validate retrieve-by-container :- [{(schema/optional-key :user-id) lib-schema/UniqueID
                                                          (schema/optional-key :item-id) lib-schema/UniqueID
                                                          (schema/optional-key :read-at) lib-schema/ISO8601}]
  [container-id :- lib-schema/UniqueID]
  (lib-read/retrieve-by-container c/dynamodb-opts container-id))

(schema/defn ^:always-validate retrieve-parts-by-container :- [{(schema/optional-key :user-id) lib-schema/UniqueID
                                                                (schema/optional-key :part-id) lib-schema/UniqueID
                                                                (schema/optional-key :item-id) lib-schema/UniqueID
                                                                (schema/optional-key :read-at) lib-schema/ISO8601}]
  [container-id :- lib-schema/UniqueID]
  (lib-read/retrieve-parts-by-container c/dynamodb-opts container-id))

(schema/defn ^:always-validate retrieve-by-org :- [{(schema/optional-key :user-id) lib-schema/UniqueID
                                                    (schema/optional-key :item-id) lib-schema/UniqueID}]
  [org-id :- lib-schema/UniqueID]
  (lib-read/retrieve-by-org c/dynamodb-opts org-id))

(schema/defn ^:always-validate retrieve-parts-by-org :- [{(schema/optional-key :user-id) lib-schema/UniqueID
                                                          (schema/optional-key :part-id) lib-schema/UniqueID}]
  [org-id :- lib-schema/UniqueID]
  (lib-read/retrieve-parts-by-org c/dynamodb-opts org-id))

;; Move

(schema/defn ^:always-validate move-item!
  [item-id :- lib-schema/UniqueID old-container-id :- lib-schema/UniqueID new-container-id :- lib-schema/UniqueID]
  (lib-read/move-item! c/dynamodb-opts item-id old-container-id new-container-id))

;; Delete

(schema/defn ^:always-validate delete-user-part!
  [user-id :- lib-schema/UniqueID part-id :- lib-schema/UniqueID]
  (lib-read/delete-user-part! c/dynamodb-opts user-id part-id))

(schema/defn ^:always-validate delete!
  [user-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (lib-read/delete-user-item! c/dynamodb-opts user-id item-id))

(schema/defn ^:always-validate delete-parts-by-item!

  ([container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
   (lib-read/delete-parts-by-item! c/dynamodb-opts item-id))

  ([item-id :- lib-schema/UniqueID]
   (lib-read/delete-parts-by-item! c/dynamodb-opts item-id)))

(schema/defn ^:always-validate delete-by-item!

  ([container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
   (lib-read/delete-by-item! c/dynamodb-opts item-id))

  ([item-id :- lib-schema/UniqueID]
   (lib-read/delete-by-item! c/dynamodb-opts item-id)))

(schema/defn ^:always-validate delete-by-part!

  ([container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID part-id :- lib-schema/UniqueID]
   (lib-read/delete-by-part! c/dynamodb-opts part-id))

  ([item-id :- lib-schema/UniqueID part-id :- lib-schema/UniqueID]
   (lib-read/delete-by-part! c/dynamodb-opts part-id))

  ([part-id :- lib-schema/UniqueID]
   (lib-read/delete-by-part! c/dynamodb-opts part-id)))

(schema/defn ^:always-validate delete-by-container!
  [container-id :- lib-schema/UniqueID]
  (lib-read/delete-by-container! c/dynamodb-opts container-id))

(schema/defn ^:always-validate delete-by-org!
  [org-id :- lib-schema/UniqueID]
  (lib-read/delete-by-org! c/dynamodb-opts org-id))

;; Count

(schema/defn ^:always-validate counts :- [{:item-id lib-schema/UniqueID
                                           :count schema/Int
                                           :last-read-at (schema/maybe lib-schema/ISO8601)}]
  [item-ids :- [lib-schema/UniqueID] user-id :- lib-schema/UniqueID]
  (lib-read/counts c/dynamodb-opts item-ids user-id))

(schema/defn ^:always-validate part-counts :- [{:part-id lib-schema/UniqueID
                                                :count schema/Int
                                                :last-read-at (schema/maybe lib-schema/ISO8601)}]
  [part-ids :- [lib-schema/UniqueID] user-id :- lib-schema/UniqueID]
  (lib-read/part-counts c/dynamodb-opts part-ids user-id))

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
       :billing-mode :pay-per-request
       :block? true}))
  (aprint
    (far/update-table config/dynamodb-opts
      read/table-name
      {:gsindexes {:operation :create
                   :name read/user-id-gsi-name
                   :billing-mode :pay-per-request
                   :hash-keydef [:user_id :s]
                   :range-keydef [:container_id :s]
                   :projection :all}}))

  ;; Add GSI for delete all via item-id
  (aprint
    @(far/update-table config/dynamodb-opts
      read/table-name
      {:gsindexes {:operation :create
                   :name read/container-id-item-id-gsi-name
                   :billing-mode :pay-per-request
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
               :billing-mode :pay-per-request
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
