(ns oc.change.resources.seen
  "Store tuples of: user-id, org-id, container-id, item-id and seen-at timestamp"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [taoensso.timbre :as timbre]
            [oc.change.config :as c]
            [oc.lib.change.resources.seen :as lib-seen]))

(def table-name (lib-seen/table-name c/dynamodb-opts))

(def entire-container lib-seen/entire-container)

(def container-id-gsi-name (lib-seen/container-id-gsi-name c/dynamodb-opts))

(def container-id-item-id-gsi-name (lib-seen/container-id-item-id-gsi-name c/dynamodb-opts))

(def org-id-user-id-gsi-name (lib-seen/org-id-user-id-gsi-name c/dynamodb-opts))

(defn- create-container-item-id [container-id item-id]
  (str container-id "-" item-id))

(schema/defn ^:always-validate store!

  ;; Store a new seen from an existing one
  ([seen-item]
   (lib-seen/store! c/dynamodb-opts seen-item))

  ;; Saw the whole container, so the item-id is a placeholder
  ([user-id :- lib-schema/UniqueID
    org-id :- lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    seen-at :- lib-schema/ISO8601]
  (lib-seen/store! c/dynamodb-opts user-id org-id container-id seen-at c/seen-ttl))

  ;; Store a seen entry for the specified user
  ([user-id :- lib-schema/UniqueID
    org-id :- lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    item-id :- lib-schema/UniqueID
    seen-at :- lib-schema/ISO8601]
  (lib-seen/store! c/dynamodb-opts user-id org-id container-id item-id seen-at c/seen-ttl)))

(schema/defn ^:always-validate delete-by-item!
  [container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (lib-seen/delete-by-item! c/dynamodb-opts container-id item-id))

(schema/defn ^:always-validate delete-by-container!
  [container-id :- lib-schema/UniqueID]
  (lib-seen/delete-by-container! c/dynamodb-opts container-id))

(schema/defn ^:always-validate move-item!
  [item-id :- lib-schema/UniqueID old-container-id :- lib-schema/UniqueID new-container-id :- lib-schema/UniqueID]
  (lib-seen/move-item! c/dynamodb-opts item-id old-container-id new-container-id))

(schema/defn ^:always-validate retrieve :- [{:container-id lib-schema/UniqueID
                                             :item-id lib-schema/UniqueID
                                             :seen-at lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID]
  (lib-seen/retrieve c/dynamodb-opts user-id))

(schema/defn ^:always-validate retrieve-by-user-org :- [{(schema/optional-key :container-id) lib-schema/UniqueID
                                                         (schema/optional-key :item-id) lib-schema/UniqueID
                                                         (schema/optional-key :seen-at) lib-schema/ISO8601}]
  [user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID]
  (lib-seen/retrieve-by-user-org c/dynamodb-opts user-id org-id))

(schema/defn ^:always-validate retrieve-by-user-container :- {(schema/optional-key :org-id) lib-schema/UniqueID
                                                              (schema/optional-key :container-id) lib-schema/UniqueID
                                                              (schema/optional-key :seen-at) lib-schema/ISO8601}
  [user-id :- lib-schema/UniqueID container-id :- lib-schema/UniqueID]
  (lib-seen/retrieve-by-user-container c/dynamodb-opts user-id container-id))

(schema/defn ^:always-validate retrieve-by-user-item :- (schema/maybe {:org-id lib-schema/UniqueID
                                                                       :container-id lib-schema/UniqueID
                                                                       :item-id lib-schema/UniqueID
                                                                       :seen-at lib-schema/ISO8601})
  [user-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (lib-seen/retrieve-by-user-item c/dynamodb-opts user-id item-id))

(schema/defn ^:always-validate retrieve-by-container-item :- [{(schema/optional-key :org-id) lib-schema/UniqueID
                                                               (schema/optional-key :container-id) lib-schema/UniqueID
                                                               (schema/optional-key :item-id) lib-schema/UniqueID
                                                               (schema/optional-key :container-item-id) lib-schema/UniqueID
                                                               (schema/optional-key :user-id) lib-schema/UniqueID
                                                               (schema/optional-key :seen-at) lib-schema/ISO8601
                                                               (schema/optional-key :seen-ttl) schema/Any}]
  [container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (lib-seen/retrieve-by-container-item c/dynamodb-opts container-id item-id))
