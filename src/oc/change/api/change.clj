(ns oc.change.api.change
  "Liberator API for change data."
  (:require [if-let.core :refer (if-let*)]
            [liberator.core :refer (defresource by-method)]
            [compojure.core :as compojure :refer (GET OPTIONS DELETE)]
            [cheshire.core :as json]
            [oc.lib.api.common :as api-common]
            [oc.change.resources.seen :as seen]
            [oc.change.resources.read :as read]
            [oc.change.config :as config]))

;; Representations
(defn- render-post-change [count]
  (json/generate-string {:post count}))

;; Resources

(defresource post-read [post-uuid]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get :delete]

  ;; Authorization
  :allowed? (by-method {
    :options true
    :get true
    :delete true
  })

  :available-media-types (by-method {:delete ["text/plain"] :get nil})

  :known-content-type? (by-method {
    :options true
    :get true
    :delete true
  })

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [read-data (read/retrieve-by-item post-uuid)
                               seen-data (seen/retrieve post-uuid)]
                              {:existing-post {:uuid post-uuid
                                               :seen seen-data
                                               :read read-data}}
                        false))

  :delete! (fn [ctx] (let [user (:user ctx)
                           user-id (:user-id user)
                           delete-item (read/delete! post-uuid user-id)]
                       (if (nil? delete-item)
                         {:deleted-items :ok}
                         {:deleted-items false})))

  ;; Responses
  :handle-ok (fn [ctx] (render-post-change (:existing-post ctx))))

;; Routes
(defn routes [sys]
  (compojure/routes
    (OPTIONS "/change/read/post/:post-uuid" [post-uuid] (post-read post-uuid))
    (OPTIONS "/change/read/post/:post-uuid/" [post-uuid] (post-read post-uuid))
    (GET "/change/read/post/:post-uuid" [post-uuid] (post-read post-uuid))
    (GET "/change/read/post/:post-uuid/" [post-uuid] (post-read post-uuid))
    (DELETE "/change/read/post/:post-uuid" [post-uuid] (post-read post-uuid))
    (DELETE "/change/read/post/:post-uuid/" [post-uuid] (post-read post-uuid))))