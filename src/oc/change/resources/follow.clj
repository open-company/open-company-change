(ns oc.change.resources.follow
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]))

(def table-name (keyword (str c/dynamodb-table-prefix "_follow")))
(def org-uuid-gsi-name (str c/dynamodb-table-prefix "_follow_gsi_org_uuid"))


(schema/defn ^:always-validate retrieve
  :- [{:user-id lib-schema/UniqueID :publisher-uuids [lib-schema/UniqueID] :org_uuid lib-schema/UniqueID}]
  [user-id :- lib-schema/UniqueID org-uuid :- lib-schema/UniqueID]
  (let [user-item (far/get-item c/dynamodb-opts table-name {:user_id user-id
                                                            :org_uuid org-uuid})]
    (map #(clojure.set/rename-keys % [:user_id :user-id :publisher_uuids :publisher-uuids :org_uuids :org-uuid]))))

(schema/defn ^:always-validate retrieve-all
  :- [{:user-id lib-schema/UniqueID :org_uuid lib-schema/UniqueID :publisher-uuids [lib-schema/UniqueID]}]
  [org-uuid :- lib-schema/UniqueID]
  (doseq [item (far/query c/dynamodb-opts table-name {:org_uuid org-uuid} {:index org-uuid-gsi-name})]
    (map #(clojure.set/rename-keys % [:user_id :user-id :org_uuid :org-uuid :publisher_uuids :publisher-uuids]))))

(schema/defn ^:always-validate store!
  [user-id :- lib-schema/UniqueID org-uuid :- lib-schema/UniqueID publisher-uuids :- [lib-schema/UniqueID]]
  (far/put-item c/dynamodb-opts table-name {
      :user_id user-id
      :org_uuid org-uuid
      :publisher_uuids publisher-uuids})
  true)

(schema/defn ^:always-validate delete!
  [user-id :- lib-schema/UniqueID org-uuid :- lib-schema/UniqueID]
  (far/delete-item c/dynamodb-opts table-name {:user_id user-id
                                               :org_uuid org-uuid})
  true)

(schema/defn ^:always-validate delete-by-org!
  [org-uuid :- lib-schema/UniqueID]
  (doseq [item (far/query c/dynamodb-opts table-name
                {:org_uuid [:eq org-uuid]} {:index org-uuid-gsi-name})]
    (delete! (:user_id item) org-uuid))
  true)

(schema/defn ^:always-validate follow!
  [user-id :- lib-schema/UniqueID org-uuid :- lib-schema/UniqueID publisher-uuid :- lib-schema/UniqueID]
  (let [item (retrieve user-id org-uuid)
        new-publisher-uuids (if (seq (:publisher_uuids item))
                              (vec (clojure.set/union (set (:publisher_uuids item)) #{publisher-uuid}))
                              [publisher-uuid])]
    (store! user-id org-uuid new-publisher-uuids))
  true)

(schema/defn ^:always-validate unfollow!
  [user-id :- lib-schema/UniqueID org-uuid :- lib-schema/UniqueID publisher-uuid :- lib-schema/UniqueID]
  (let [item (retrieve user-id org-uuid)
        new-publisher-uuids (if (seq (:publisher_uuids item))
                              (vec (clojure.set/difference (set (:publisher_uuids item)) #{publisher-uuid}))
                              [])]
    (if (seq new-publisher-uuids)
      (store! user-id org-uuid new-publisher-uuids)
      (delete! user-id org-uuid)))
  true)

(comment

  (require '[oc.change.resources.follow :as follow] :reload)

  (far/list-tables c/dynamodb-opts)

  (far/delete-table c/dynamodb-opts follow/table-name)
  (aprint
   (far/create-table c/dynamodb-opts
     follow/table-name
     [:user_id :s]
     {:range-keydef [:org_uuid :s]
      :billing-mode :pay-per-request
      :block? true}))

  (aprint
   @(far/update-table c/dynamodb-opts
     follow/table-name
     {:gsindexes {:operation :create
                  :name follow/org-uuid-gsi-name
                  :billing-mode :pay-per-request
                  :hash-keydef [:org_uuid :s]
                  :range-keydef [:user_id :s]
                  :projection :keys-only}}))

  (aprint (far/describe-table c/dynamodb-opts follow/table-name))
  
  (follow/store! "1111-1111-1111" "aaaa-aaaa-aaaa" ["2222-2222-2222" "3333-3333-3333"])

  (follow/follow! "1111-1111-1111" "aaaa-aaaa-aaaa" "4444-4444-4444")

  (follow/unfollow! "1111-1111-1111" "aaaa-aaaa-aaaa" "2222-2222-2222")

  (follow/retrieve "1111-1111-1111" "aaaa-aaaa-aaaa")
  (follow/retrieve-all "aaaa-aaaa-aaaa")

  (follow/delete! "1111-1111-1111" "aaaa-aaaa-aaaa")

  (follow/delete-by-org! "aaaa-aaaa-aaaa"))