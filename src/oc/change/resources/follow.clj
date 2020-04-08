(ns oc.change.resources.follow
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]
            [oc.lib.slugify :as slug]
            [oc.change.config :as c]))

(def table-name (keyword (str c/dynamodb-table-prefix "_follow")))
(def org-slug-gsi-name (str c/dynamodb-table-prefix "_follow_gsi_org_slug"))

(def Slug (schema/pred slug/valid-slug?))

(schema/defn ^:always-validate retrieve
  :- {:user-id lib-schema/UniqueID :publisher-uuids [lib-schema/UniqueID] :org-slug Slug}
  [user-id :- lib-schema/UniqueID org-slug :- Slug]
  (if-let [user-item (far/get-item c/dynamodb-opts table-name {:user_id user-id
                                                               :org_slug org-slug})]
    (clojure.set/rename-keys user-item {:user_id :user-id :publisher_uuids :publisher-uuids :org_slug :org-slug})
    {:user-id user-id :org-slug org-slug :publisher-uuids []}))

(schema/defn ^:always-validate retrieve-all
  :- [{:user-id lib-schema/UniqueID :org_slug Slug :publisher-uuids [lib-schema/UniqueID]}]
  [org-slug :- Slug]
  (doseq [item (far/query c/dynamodb-opts table-name {:org_slug org-slug} {:index org-slug-gsi-name})]
    (map #(clojure.set/rename-keys % {:user_id :user-id :org_slug :org-slug :publisher_uuids :publisher-uuids}))))

(schema/defn ^:always-validate store!
  [user-id :- lib-schema/UniqueID org-slug :- Slug publisher-uuids :- [lib-schema/UniqueID]]
  (far/put-item c/dynamodb-opts table-name {
      :user_id user-id
      :org_slug org-slug
      :publisher_uuids publisher-uuids})
  true)

(schema/defn ^:always-validate delete!
  [user-id :- lib-schema/UniqueID org-slug :- Slug]
  (far/delete-item c/dynamodb-opts table-name {:user_id user-id
                                               :org_slug org-slug})
  true)

(schema/defn ^:always-validate delete-by-org!
  [org-slug :- Slug]
  (doseq [item (far/query c/dynamodb-opts table-name
                {:org_slug [:eq org-slug]} {:index org-slug-gsi-name})]
    (delete! (:user_id item) org-slug))
  true)

(schema/defn ^:always-validate follow!
  [user-id :- lib-schema/UniqueID org-slug :- Slug publisher-uuid :- lib-schema/UniqueID]
  (let [item (retrieve user-id org-slug)
        new-publisher-uuids (if (seq (:publisher_uuids item))
                              (vec (clojure.set/union (set (:publisher_uuids item)) #{publisher-uuid}))
                              [publisher-uuid])]
    (store! user-id org-slug new-publisher-uuids))
  true)

(schema/defn ^:always-validate unfollow!
  [user-id :- lib-schema/UniqueID org-slug :- Slug publisher-uuid :- lib-schema/UniqueID]
  (let [item (retrieve user-id org-slug)
        new-publisher-uuids (if (seq (:publisher_uuids item))
                              (vec (clojure.set/difference (set (:publisher_uuids item)) #{publisher-uuid}))
                              [])]
    (if (seq new-publisher-uuids)
      (store! user-id org-slug new-publisher-uuids)
      (delete! user-id org-slug)))
  true)

(comment

  (require '[oc.change.resources.follow :as follow] :reload)

  (far/list-tables c/dynamodb-opts)

  (far/delete-table c/dynamodb-opts follow/table-name)
  (aprint
   (far/create-table c/dynamodb-opts
     follow/table-name
     [:user_id :s]
     {:range-keydef [:org_slug :s]
      :billing-mode :pay-per-request
      :block? true}))

  (aprint
   @(far/update-table c/dynamodb-opts
     follow/table-name
     {:gsindexes {:operation :create
                  :name follow/org-slug-gsi-name
                  :billing-mode :pay-per-request
                  :hash-keydef [:org_slug :s]
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