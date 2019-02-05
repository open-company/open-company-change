(ns oc.change.api.change
  "Liberator API for change data."
  (:require [taoensso.timbre :as timbre]
            [if-let.core :refer (if-let*)]
            [liberator.core :refer (defresource by-method)]
            [compojure.core :as compojure :refer (GET ANY)]
            [cheshire.core :as json]
            [oc.lib.api.common :as api-common]
            [oc.change.resources.seen :as seen]
            [oc.change.resources.read :as read]
            [oc.change.config :as config]))

;; Representations
(defn- render-count [count]
  (json/generate-string {:post count}))

;; Resources

(defresource post-read [post-uuid]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  ;;:available-media-types (by-method {
  ;;                          :get [user-rep/collection-media-type]})
  ;;:handle-not-acceptable (by-method {
  ;;                          :get (api-common/only-accept 406 user-rep/collection-media-type)})

  ;; Authorization
  :allowed? (by-method {
    :options true
    :get true
  })

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [read-data (read/retrieve post-uuid)
                               seen-data (seen/retrieve post-uuid)]
                              {:existing-post {:uuid post-uuid
                                               :seen seen-data
                                               :read read-data}}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (render-count (:existing-post ctx))))

;; Routes
(defn routes [sys]
  (compojure/routes
   ;; Assignee list operations
      (ANY "/change/read/post/:post-uuid"
        [post-uuid]
        (post-read post-uuid))))
