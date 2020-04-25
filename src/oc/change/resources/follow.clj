(ns oc.change.resources.follow
  (:require [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]
            [oc.lib.change.resources.follow :as lib-follow]))

(defn table-name []
  (lib-follow/table-name c/dynamodb-opts))

(def org-slug-gsi-name (str c/dynamodb-table-prefix "_follow_gsi_org_slug"))

(defn publisher-follower-table-name []
  (lib-follow/publisher-follower-table-name c/dynamodb-opts))

(schema/defn ^:always-validate retrieve
  :- {:user-id lib-schema/UniqueID :org-slug lib-follow/Slug :publisher-uuids (schema/maybe [lib-schema/UniqueID]) :board-uuids (schema/maybe [lib-schema/UniqueID])}
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug]
  (lib-follow/retrieve c/dynamodb-opts user-id org-slug))

(schema/defn ^:always-validate retrieve-all
  :- [{:user-id lib-schema/UniqueID :org-slug lib-follow/Slug :publisher-uuids (schema/maybe [lib-schema/UniqueID]) :board-uuids (schema/maybe [lib-schema/UniqueID])}]
  [org-slug :- lib-follow/Slug]
  (lib-follow/retrieve-all c/dynamodb-opts org-slug))

(schema/defn ^:always-validate retrieve-publisher-followers
  :- {:publisher-uuid lib-schema/UniqueID :org-slug lib-follow/Slug :follower-uuids [lib-schema/UniqueID]}
  [org-slug :- lib-follow/Slug publisher-uuid :- lib-schema/UniqueID]
  (lib-follow/retrieve-publisher-followers c/dynamodb-opts org-slug publisher-uuid))

(schema/defn ^:always-validate retrieve-board-followers
  :- {:board-uuid lib-schema/UniqueID :org-slug lib-follow/Slug :follower-uuids [lib-schema/UniqueID]}
  [org-slug :- lib-follow/Slug board-uuid :- lib-schema/UniqueID]
  (lib-follow/retrieve-board-followers c/dynamodb-opts org-slug board-uuid))

(schema/defn ^:always-validate retrieve-all-publisher-followers
  :- [{:org-slug lib-follow/Slug :follower-uuids [lib-schema/UniqueID] :publisher-uuid lib-schema/UniqueID :resource-type lib-follow/ResourceType}]
  [org-slug :- lib-follow/Slug]
  (lib-follow/retrieve-all-publisher-followers c/dynamodb-opts org-slug))

(schema/defn ^:always-validate retrieve-all-board-followers
  :- [{:org-slug lib-follow/Slug :follower-uuids [lib-schema/UniqueID] :board-uuid lib-schema/UniqueID :resource-type lib-follow/ResourceType}]
  [org-slug :- lib-follow/Slug]
  (lib-follow/retrieve-all-board-followers c/dynamodb-opts org-slug))

(schema/defn ^:always-validate store!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug publisher-uuids :- [lib-schema/UniqueID] board-uuids :- [lib-schema/UniqueID]]
  (lib-follow/store! c/dynamodb-opts user-id org-slug publisher-uuids board-uuids))

(schema/defn ^:always-validate store-publishers!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug publisher-uuids :- [lib-schema/UniqueID]]
  (lib-follow/store-publishers! c/dynamodb-opts user-id org-slug publisher-uuids))

(schema/defn ^:always-validate store-boards!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug board-uuids :- [lib-schema/UniqueID]]
  (lib-follow/store-boards! c/dynamodb-opts user-id org-slug board-uuids))

(schema/defn ^:always-validate delete!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug]
  (lib-follow/delete! c/dynamodb-opts user-id org-slug))

(schema/defn ^:always-validate delete-by-org!
  [org-slug :- lib-follow/Slug]
  (lib-follow/delete-by-org! c/dynamodb-opts org-slug))

(schema/defn ^:always-validate follow-publisher!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug publisher-uuid :- lib-schema/UniqueID]
  (lib-follow/follow-publisher! c/dynamodb-opts user-id org-slug publisher-uuid))

(schema/defn ^:always-validate follow-board!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug board-uuid :- lib-schema/UniqueID]
  (lib-follow/follow-board! c/dynamodb-opts user-id org-slug board-uuid))

(schema/defn ^:always-validate unfollow-publisher!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug publisher-uuid :- lib-schema/UniqueID]
  (lib-follow/unfollow-publisher! c/dynamodb-opts user-id org-slug publisher-uuid))

(schema/defn ^:always-validate unfollow-board!
  [user-id :- lib-schema/UniqueID org-slug :- lib-follow/Slug board-uuid :- lib-schema/UniqueID]
  (lib-follow/unfollow-board! c/dynamodb-opts user-id org-slug board-uuid))