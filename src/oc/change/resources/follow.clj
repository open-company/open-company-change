(ns oc.change.resources.follow
  (:require [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]
            [oc.lib.change.resources.follow :as lib-follow]))

(defn table-name []
  (lib-follow/table-name c/dynamodb-opts))

(def org-slug-gsi-name (str c/dynamodb-table-prefix "_follow_gsi_org_slug"))

(defn follower-table-name []
  (lib-follow/follower-table-name c/dynamodb-opts))

(schema/defn ^:always-validate retrieve
  :- {:user-id lib-schema/UniqueID :publisher-uuids [lib-schema/UniqueID] :org-slug lib-follow/Slug}
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug]
  (lib-follow/retrieve c/dynamodb-opts user-id org-slug))

(schema/defn ^:always-validate retrieve-all
  :- [{:user-id lib-schema/UniqueID :org_slug lib-follow/Slug :publisher-uuids [lib-schema/UniqueID]}]
  [org-slug :- lib-follow/Slug]
  (lib-follow/retrieve-all c/dynamodb-opts org-slug))

(schema/defn ^:always-validate store!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug publisher-uuids :- [lib-schema/UniqueID]]
  (lib-follow/store! c/dynamodb-opts user-id org-slug publisher-uuids))

(schema/defn ^:always-validate delete!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug]
  (lib-follow/delete! c/dynamodb-opts user-id org-slug))

(schema/defn ^:always-validate delete-by-org!
  [org-slug :- lib-follow/Slug]
  (lib-follow/delete-by-org! c/dynamodb-opts org-slug))

(schema/defn ^:always-validate follow!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug publisher-uuid :- lib-schema/UniqueID]
  (lib-follow/follow! c/dynamodb-opts user-id org-slug publisher-uuid))

(schema/defn ^:always-validate unfollow!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug publisher-uuid :- lib-schema/UniqueID]
  (lib-follow/unfollow! c/dynamodb-opts user-id org-slug publisher-uuid))