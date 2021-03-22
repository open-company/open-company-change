(ns oc.change.resources.follow
  (:require [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.change.config :as c]
            [oc.lib.change.resources.follow :as lib-follow]))

(def table-name (lib-follow/table-name c/dynamodb-opts))

(defn org-id-gsi-name []
  (lib-follow/org-id-gsi-name c/dynamodb-opts))

(defn board-unfollower-table-name []
  (lib-follow/board-unfollower-table-name c/dynamodb-opts))

(schema/defn ^:always-validate retrieve
  :- [{:user-id lib-schema/UniqueID :org-id lib-schema/UniqueID :unfollow-board-uuids (schema/maybe [lib-schema/UniqueID])}]
  [org-id :- lib-schema/UniqueID]
  (lib-follow/retrieve c/dynamodb-opts org-id))

(schema/defn ^:always-validate retrieve-all
  :- [{:user-id lib-schema/UniqueID :org-id lib-schema/UniqueID :unfollow-board-uuids (schema/maybe [lib-schema/UniqueID])}]
  [org-id :- lib-schema/UniqueID]
  (lib-follow/retrieve-all c/dynamodb-opts org-id))

(schema/defn ^:always-validate retrieve-board-unfollowers
  :- {:board-uuid lib-schema/UniqueID :org-id lib-schema/UniqueID :unfollower-uuids [lib-schema/UniqueID]}
  [org-id :- lib-schema/UniqueID unfollow-board-uuid :- lib-schema/UniqueID]
  (lib-follow/retrieve-board-unfollowers c/dynamodb-opts org-id unfollow-board-uuid))

(schema/defn ^:always-validate retrieve-all-board-unfollowers
  :- [{:org-id lib-schema/UniqueID :unfollower-uuids [lib-schema/UniqueID] :board-uuid lib-schema/UniqueID :resource-type lib-follow/ResourceType}]
  [org-id :- lib-schema/UniqueID]
  (lib-follow/retrieve-all-board-unfollowers c/dynamodb-opts org-id))

(schema/defn ^:always-validate store!
  [user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID unfollow-board-uuids :- [lib-schema/UniqueID]]
  (lib-follow/store! c/dynamodb-opts user-id org-id unfollow-board-uuids))

(schema/defn ^:always-validate store-boards!
  [user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID unfollow-board-uuids :- [lib-schema/UniqueID]]
  (lib-follow/store-boards! c/dynamodb-opts user-id org-id unfollow-board-uuids))

(schema/defn ^:always-validate delete!
  [user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID]
  (lib-follow/delete! c/dynamodb-opts user-id org-id))

(schema/defn ^:always-validate delete-by-org!
  [org-id :- lib-schema/UniqueID]
  (lib-follow/delete-by-org! c/dynamodb-opts org-id))

(schema/defn ^:always-validate follow-board!
  [user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID board-uuid :- lib-schema/UniqueID]
  (lib-follow/follow-board! c/dynamodb-opts user-id org-id board-uuid))

(schema/defn ^:always-validate unfollow-board!
  [user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID board-uuid :- lib-schema/UniqueID]
  (lib-follow/unfollow-board! c/dynamodb-opts user-id org-id board-uuid))