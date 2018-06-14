(ns oc.change.async.persistence
  "
  Persist seen events and chage events.

  Use of this persistence is through core/async. A message is sent to the `persistence-chan`.
  "
  (:require [clojure.core.async :as async :refer (>!! <!)]
            [defun.core :refer (defun-)]
            [taoensso.timbre :as timbre]
            [oc.lib.async.watcher :as watcher]
            [oc.change.resources.seen :as seen]
            [oc.change.resources.change :as change]))

;; ----- core.async -----

(defonce persistence-chan (async/chan 10000)) ; buffered channel

(defonce persistence-go (atom true))

;; ----- Utility methods -----

(defun- persist
  
  ;; Add an entry
  ([:add :entry container-id item-id author-id change-at]
  (timbre/info "Persisting entry change on:" item-id "for container:" container-id "and author:" author-id)
  (pmap #(change/store! % item-id change-at) [container-id author-id]))

  ;; Delete an entry

  ;; Delete a board

  ;; Else
  ([_op _resource _container _item _author _change]
  (timbre/trace "No persistence needed.")))

(defn- unseen-items-for [container-id all-changes all-seens]
  (let [changes (filter #(= container-id (:container-id %)) all-changes) ; only changes for this container-id
        changed-items (set (map :item-id changes)) ; item ids of the changes for this container
        seens (filter #(= container-id (:container-id %)) all-seens) ; only seens for this container-id
        seen-items (set (map :item-id seens))] ; item ids seen in the container
    (vec (clojure.set/difference changed-items seen-items))))

(defn status-for
  "

  Given a set of changes to items in containers...

  Changes:
  {:container-id '1111-1111-1111', :item-id '2222-2222-2222', :change-at '2018-06-10T14:49:50.883Z'}
  {:container-id '1111-1111-1111', :item-id '3333-3333-3333', :change-at '2018-06-11T14:49:50.986Z'}
  {:container-id '4444-4444-4444', :item-id '5555-5555-5555', :change-at '2018-06-12T14:49:57.107Z'})

  And a set of seen events for the user (where '9999-9999-9999' means they saw everything in the container)...

  Seens:
  {:container-id '1111-1111-1111', :item-id '2222-2222-2222', :seen-at '2018-06-11T11:23:51.395Z'}
  {:container-id '4444-4444-4444', :item-id '9999-9999-9999', :seen-at '2018-06-13T11:23:51.395Z'}

  Returns a status for each container with the changes that haven't been seen....

  Status:
  {:container-id '1111-1111-1111' :unseen ['3333-3333-3333']}
  {:container-id '4444-4444-4444' :unseen []}

  In the above example, item '2222-2222-2222' was seen, item '3333-3333-3333' was not, and item '5555-5555-5555'
  was seen because the whole '4444-4444-4444' container was seen.
  "
  [container-ids changes seens]
  (pmap #(hash-map :container-id % :unseen (unseen-items-for % changes seens)) container-ids))

;; ----- Event handling -----

(defun- handle-persistence-message
  "
  Handles 3 types of messages: status, seen, and change

  NB: Uses 'blocking' core.async put `>!!`, not `parked` core.async put `>!` because even though this
  is called from inside a go block, it's also inside an `async/thread`.
  "

  ([message :guard :status]
  ;; Lookup when a specified user saw specified containers and when the specified containers saw changes
  ;; Send the merger of the 2 (by container-id) to the sender channel as a status message
  (let [user-id (:user-id message)
        container-ids (:container-ids message)
        client-id (:client-id message)]
    (timbre/info "Status request for:" container-ids "by:" user-id "/" client-id)
    (let [seens (filter #(some (fn [x] (= x (:container-id %))) container-ids) (seen/retrieve user-id))
          changes (change/retrieve container-ids)
          status (status-for container-ids changes seens)]
      (>!! watcher/sender-chan {:event [:container/status status]
                                :client-id client-id}))))

  ([message :guard :seen]
  ;; Persist that a specified user saw a specified container at a specified time
  (let [user-id (:user-id message)
        container-id (:container-id message)
        item-id (:item-id message)
        publisher-id (:publisher-id message)
        seen-at (:seen-at message)]
    (timbre/info "Seen request for user:" user-id "on:" container-id "at:" seen-at)
    (if (and item-id publisher-id)
      ;; upsert an item seen entry for the container and the author
      (pmap #(seen/store! user-id % item-id seen-at) [item-id publisher-id]) 
      ;; upsert a seen entry for the container (container here may be the author)
      (seen/store! user-id container-id seen-at)))) 

  ([message :guard :change]
  ; Persist that a container received a new item at a specific time
  (let [container-id (:container-id message)
        item-id (:item-id message)
        resource-type (:resource-type message)
        change-type (:change-type message)
        author-id (:author-id message)
        change-at (:change-at message)]
    (timbre/info resource-type change-type "request on:" item-id  "in:" container-id
                                           "by:" author-id "at:" change-at)
    (persist change-type resource-type container-id item-id author-id change-at)))

  ([message]
  (timbre/warn "Unknown request in persistence channel" message)))

;; ----- Persistence event loop -----

(defn persistence-loop []
  (reset! persistence-go true)
  (timbre/info "Starting persistence...")
  (async/go (while @persistence-go
    (timbre/debug "Persistence waiting...")
    (let [message (<! persistence-chan)]
      (timbre/debug "Processing message on persistence channel...")
      (if (:stop message)
        (do (reset! persistence-go false) (timbre/info "Persistence stopped."))
        (async/thread
          (try
            (handle-persistence-message message)
          (catch Exception e
            (timbre/error e)))))))))

;; ----- Component start/stop -----

(defn start
  "Start the core.async loop for persisting events."
  []
  (persistence-loop))

(defn stop
  "Stop the the core.async loop persisting events."
  []
  (when @persistence-go
    (timbre/info "Stopping persistence...")
    (>!! persistence-chan {:stop true})))