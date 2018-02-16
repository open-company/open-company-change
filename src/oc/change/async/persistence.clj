(ns oc.change.async.persistence
  "
  Persist seen events and chage events.

  Use of this persistence is through core/async. A message is sent to the `persistence-chan`.
  "
  (:require [clojure.core.async :as async :refer (>!! <!)]
            [defun.core :refer (defun-)]
            [taoensso.timbre :as timbre]
            [oc.change.resources.user :as u]
            [oc.lib.async.watcher :as watcher]
            [oc.change.resources.container :as container]))

;; ----- core.async -----

(defonce persistence-chan (async/chan 10000)) ; buffered channel

(defonce persistence-go (atom true))

;; ----- Event handling -----

(defun- handle-persistence-message
  "
  Handles 3 types of messages: status, seen, and change

  NB: Uses 'blocking' core.async put `!!>`, not `parked` core.async put `!>` because even though this
  is called from inside a go block, it's also inside an `async/thread`.
  "

  ([message :guard :status]
  ;; Lookup when a specified user saw specified containers and when the specified containers saw change
  ;; Send the merger of the 2 (by contianer-id) to the sender channel as a status message
  (let [user-id (:user-id message)
        container-ids (:container-ids message)
        client-id (:client-id message)]
    (timbre/info "Status request for:" container-ids "by:" user-id "/" client-id)
    (let [seens (u/seen user-id container-ids)
          changes (container/change container-ids)
          status (map #(apply merge %) (vals (merge-with concat
                                                (group-by :container-id seens)
                                                (group-by :container-id changes))))]
      (>!! watcher/sender-chan {:event [:container/status status]
                                :client-id client-id}))))

  ([message :guard :seen]
  ;; Persist that a specified user saw a specified container at a specified time
  (let [user-id (:user-id message)
        container-id (:container-id message)
        seen-at (:seen-at message)]
    (timbre/info "Seen request for:" user-id "on:" container-id "at:" seen-at)
    (u/seen! user-id container-id seen-at)))

  ([message :guard :change]
  ; Persist that a specified user saw a specified container at a specified time
  (let [container-id (:container-id message)
        change-at (:change-at message)]
    (timbre/info "Change request for:" container-id "at:" change-at)
    (container/change! container-id change-at)))

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