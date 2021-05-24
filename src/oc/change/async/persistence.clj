(ns oc.change.async.persistence
  "
  Persist seen events and change events.

  Use of this persistence is through core/async. A message is sent to the `persistence-chan`.
  "
  (:require [clojure.core.async :as async :refer (>!! <!)]
            [defun.core :refer (defun-)]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]
            [oc.lib.async.watcher :as watcher]
            [oc.change.resources.seen :as seen]
            [oc.change.resources.change :as change]
            [oc.change.resources.read :as read]
            [oc.change.resources.follow :as follow]))

;; ----- core.async -----

(defonce persistence-chan (async/chan 10000)) ; buffered channel

(defonce persistence-go (atom true))

;; ----- Utility methods -----

(defun- persist

  ;; Add an entry
  ([:add :entry container-id item-id author-id change-at _new-item _old-item]
  (timbre/info "Persisting add entry change on:" item-id "for container:" container-id "and author:" author-id)
  (pmap #(change/store! % item-id change-at) [container-id author-id]))

  ;; Delete an entry
  ([:delete :entry container-id item-id _author-id _change-at _new-item _old-item]
  (timbre/info "Persisting delete entry change on:" item-id "for container:" container-id)
  (change/delete-by-item! container-id item-id)
  ;; Delete the read/seen records only if it's not a draft
  (when (lib-schema/unique-id? container-id)
    (seen/delete-by-item! container-id item-id)
    (read/delete-by-item! container-id item-id)))

  ([:move :entry container-id item-id _author-id _change-at new-item old-item]
  (timbre/info "Persisting move entry change for item:" item-id "from container:" (:board-uuid old-item) "to container:" (:board-uuid new-item))
  (change/move-item! item-id (:board-uuid old-item) (:board-uuid new-item))
  (seen/move-item! item-id (:board-uuid old-item) (:board-uuid new-item))
  (read/move-item! item-id (:board-uuid old-item) (:board-uuid new-item)))

  ;; Delete a board
  ([:delete :board container-id _item-id _author-id _change-at _new-item _old-item]
  (timbre/info "Persisting delete board change for container:" container-id)
  (change/delete-by-container! container-id)
  (seen/delete-by-container! container-id)
  (read/delete-by-container! container-id))

  ;; Follow publishers
  ([:follow :publishers user-id org-slug publisher-uuids :guard coll?]
  (timbre/info "Persisting follow publishers for user:" user-id "of org:" org-slug "publishers:" publisher-uuids)
  (follow/store-publishers! user-id org-slug publisher-uuids))

  ;; Unfollow boards
  ([:unfollow :boards user-id org-slug board-uuids :guard coll?]
  (timbre/info "Persisting follow boards for user:" user-id "of org:" org-slug "boards:" board-uuids)
  (follow/store-boards! user-id org-slug board-uuids))

  ;; Follow publisher
  ([:follow :publisher user-id org-slug publisher-uuid :guard string?]
  (timbre/info "Persisting follow for user:" user-id "of org:" org-slug "publisher:" publisher-uuid)
  (follow/follow-publisher! user-id org-slug publisher-uuid))

  ;; Follow board
  ([:follow :board user-id org-slug board-uuid :guard string?]
  (timbre/info "Persisting follow for user:" user-id "of org:" org-slug "board:" board-uuid)
  (follow/follow-board! user-id org-slug board-uuid))

  ;; Unfollow publisher
  ([:unfollow :publisher user-id org-slug publisher-uuid :guard string?]
  (timbre/info "Persisting unfollow for user:" user-id "of org:" org-slug "publisher:" publisher-uuid)
  (follow/unfollow-publisher! user-id org-slug publisher-uuid))

  ;; Follow board
  ([:unfollow :board user-id org-slug board-uuid :guard string?]
  (timbre/info "Persisting unfollow for user:" user-id "of org:" org-slug "board:" board-uuid)
  (follow/unfollow-board! user-id org-slug board-uuid))

  ;; Else
  ([_op _resource _container _item _author _change _new-item _old-item]
  (timbre/trace "No persistence needed.")))

(defn- unseen-items-for
  "
  Implements the unseen item logic based on changes in the container, when the container was seen, and when
  individual items were seen.
  "
  [container-id all-changes all-seens]
  (let [changes (filter #(= container-id (:container-id %)) all-changes) ; only changes for this container-id
        seens (filter #(= container-id (:container-id %)) all-seens) ; only seens for this container-id
        container-seen (some #(when (= (:item-id %) seen/entire-container) (:seen-at %)) seens) ; container seen at
        new-changes (if container-seen
                          (filter #(pos? (compare (:change-at %) container-seen)) changes)
                          changes)
        changed-items (set (map :item-id new-changes)) ; item ids of the changes for this container
        seen-items (set (map :item-id seens))] ; item ids seen in the container
    ; return the differences newly changed items & already seen items
    (vec (clojure.set/difference changed-items seen-items))))

(defn- last-item-seen-for [container-id all-seens]
  (->> all-seens
       (filter #(= container-id (:container-id %)))
       (sort-by :seen-at)
       (vec)
       (reverse)
       first
       :seen-at))


(defn- unread-items-for
  "
  Implements the unread item logic based on changes in the container, when
  individual items were read.
  "
  [container-id all-changes all-reads]
  (let [changes (filter #(= container-id (:container-id %)) all-changes) ; only changes for this container-id
        reads (filter #(= container-id (:container-id %)) all-reads) ; only reads for this container-id
        changed-items (set (map :item-id changes)) ; item ids of the changes for this container
        read-items (set (map :item-id reads))] ; item ids read in the container
    ; return the differences newly changed items & already read items
    (vec (clojure.set/difference changed-items read-items))))

(defn status-for
  "
  Given a set of changes to items in containers...

  Changes:
  {:container-id '1111-1111-1111', :item-id '2222-2222-2222', :change-at '2018-06-10T14:49:50.883Z'}
  {:container-id '1111-1111-1111', :item-id '3333-3333-3333', :change-at '2018-06-11T14:49:50.986Z'}
  {:container-id '1111-1111-1111', :item-id '6666-6666-6666', :change-at '2018-06-14T14:49:50.107Z'}
  {:container-id '4444-4444-4444', :item-id '5555-5555-5555', :change-at '2018-06-12T14:49:57.107Z'})

  A set of seen events for the user (where '9999-9999-9999' means they saw everything in the container)...

  Seens:
  {:container-id '1111-1111-1111', :item-id '2222-2222-2222', :seen-at '2018-06-11T11:23:51.395Z'}
  {:container-id '4444-4444-4444', :item-id '9999-9999-9999', :seen-at '2018-06-13T11:23:51.395Z'}

  A set of read events for the user...

  Reads:
  {:container-id '1111-1111-1111', :item-id '2222-2222-2222', :read-at '2018-06-11T13:23:51.395Z'}
  {:container-id '1111-1111-1111', :item-id '3333-3333-3333', :read-at '2018-06-13T15:23:51.395Z'}

  Returns a status for each container with the changes that haven't been seen....

  Status:
  {:container-id '1111-1111-1111' :unseen ['3333-3333-3333'] :unread ['6666-6666-6666']}
  {:container-id '4444-4444-4444' :unseen [] :unread ['5555-5555-5555']}

  In the above example, item '2222-2222-2222' was seen, item '3333-3333-3333' was not, and item '5555-5555-5555'
  was seen because the whole '4444-4444-4444' container was seen.
  "
  [container-ids changes seens reads]
  (timbre/debug "Check status for containers:" container-ids "with changes:" (vec changes) "and seens:" (vec seens))
  (pmap #(hash-map :container-id %
                   :unseen (unseen-items-for % changes seens)
                   :unread (unread-items-for % changes reads)
                   :last-seen-at (last-item-seen-for % seens))
   container-ids))

;; ----- Event handling -----

(defun- handle-persistence-message
  "
  Handles 3 types of messages: status, seen, and change

  NB: Uses 'blocking' core.async put `>!!`, not `parked` core.async put `>!` because even though this
  is called from inside a go block, it's also inside an `async/thread`.
  "

  ;; READS

  ([message :guard :status]
  ;; Lookup when a specified user saw specified containers, when the specified containers saw changes,
  ;; and when the specified user had reads in specified containers.
  ;; Send the processed result to the sender's channel as a container/status message
  (let [user-id (:user-id message)
        container-ids (:container-ids message)
        client-id (:client-id message)
        just-seen (:just-seen message)] ; an entry just written to the DB that might not be readable yet
    (timbre/info "Status request for:" container-ids "by:" user-id "/" client-id)
    (let [seens (filter #((set container-ids) (:container-id %)) (seen/retrieve user-id))
          all-seens (if just-seen (conj seens just-seen) seens) ; avoid a race condition in the DB
          all-reads (filter #((set container-ids) (:container-id %)) (read/retrieve-by-user user-id))
          changes (change/retrieve container-ids)
          status (status-for container-ids changes all-seens all-reads)]
      (>!! watcher/sender-chan {:event [:container/status status]
                                :client-id client-id}))))

 ([message :guard :who-read]
  ;; Lookup who read a specified item
  ;; Send the result to the sender's channel as an item/status message
  (let [item-id (:item-id message)
        client-id (:client-id message)]
    (timbre/info "Who read request for:" item-id "by:" client-id)
    (let [reads (read/retrieve-by-item item-id)
          status {:item-id item-id :reads reads}]
      (>!! watcher/sender-chan {:event [:item/status status]
                                :client-id client-id}))))

 ([message :guard :who-read-count]
  ;; Lookup how many reads are there for each of a sequence of specified items
  ;; Send the result to the sender's channel as an item/counts message
  (let [item-ids (:item-ids message)
        client-id (:client-id message)
        user-id (:user-id message)]
    (timbre/info "Who read cound request for:" item-ids "by:" client-id "for:" user-id)
    (let [reads (read/counts item-ids user-id)]
      (>!! watcher/sender-chan {:event [:item/counts reads]
                                :client-id client-id}))))

  ;; WRITES

  ([message :guard :seen]
  ;; Persist that a specified user saw a specified container at a specified time
  (let [user-id (:user-id message)
        org-id (:org-id message)
        container-id (:container-id message)
        item-id (:item-id message)
        publisher-id (:publisher-id message)
        seen-at (:seen-at message)
        just-seen (select-keys message [:container-id :item-id :seen-at])]
    (timbre/info "Seen request for user:" user-id "on:" container-id "at:" seen-at)
    (if (and item-id publisher-id)
      ;; upsert an item seen entry for the container and the author
      (pmap #(seen/store! user-id org-id % item-id seen-at) [container-id publisher-id])
      ;; upsert a seen entry for the container (NB: container here may also be a user, the author)
      (seen/store! user-id org-id container-id seen-at))
    ;; recurse after upserting the message so it seems the client asked for status on the seen container...
    ;; in this way the client will receive an updated container/status message for this container
    (handle-persistence-message (-> message
                                  (dissoc :seen)
                                  (assoc :just-seen just-seen)
                                  (assoc :status true)
                                  (assoc :container-ids [container-id])))))

  ([message :guard :read]
  ;; Persist that a specified user read a specified item
  (let [org-id (:org-id message)
        user-id (:user-id message)
        container-id (:container-id message)
        item-id (:item-id message)
        user-name (:name message)
        avatar-url (:avatar-url message)
        read-at (:read-at message)]
    (timbre/info "Read request for user:" user-id "for item:" item-id "at:" read-at " org: " org-id " container: " container-id " name: " user-name " avatar: " avatar-url)
    (read/store! org-id container-id item-id user-id user-name avatar-url read-at)
    ;; Send an item/status to everyone watching this container so they get the updated list of readers
    (let [reads (read/retrieve-by-item item-id)
          status {:item-id item-id :reads reads}]
      (>!! watcher/watcher-chan {:send true
                                 :watch-id container-id
                                 :event :item/status
                                 :payload status}))))

  ([message :guard :change]
  ; Persist that a container received a new item at a specific time
  (let [container-id (:container-id message)
        item-id (:item-id message)
        resource-type (:resource-type message)
        change-type (:change-type message)
        author-id (:author-id message)
        change-at (:change-at message)
        new-item (:new-item message)
        old-item (:old-item message)]
    (timbre/info resource-type change-type "request on:" item-id  "in:" container-id
                                           "by:" author-id "at:" change-at)
    (persist change-type resource-type container-id item-id author-id change-at new-item old-item)))

  ;; List following
  ([message :guard :follow-list]
  ; Persist that a container received a new item at a specific time
  (let [user-id (:user-id message)
        org-slug (:org-slug message)
        client-id (:client-id message)
        following-data (follow/retrieve user-id org-slug)]
    (timbre/info "Follow list request from:" user-id  "on:" org-slug)
    (>!! watcher/sender-chan {:event [:follow/list following-data]
                              :client-id client-id})))

  ;; Followers count list
  ([message :guard :followers-count]
  ; Persist that a container received a new item at a specific time
  (let [user-id (:user-id message)
        org-slug (:org-slug message)
        client-id (:client-id message)
        publisher-followers (follow/retrieve-all-publisher-followers org-slug)
        board-unfollowers (follow/retrieve-all-board-unfollowers org-slug)
        followers (mapv #(-> %
                          (assoc :resource-uuid (or (:publisher-uuid %) (:board-uuid %)))
                          (assoc :count (or (-> % :unfollower-uuids count) (-> % :follower-uuids count)))
                          (dissoc :publisher-uuid :board-uuid :follower-uuids :unfollower-uuids))
                   (concat publisher-followers board-unfollowers))]
    (timbre/info "Followers count request from:" user-id  "on:" org-slug)
    (>!! watcher/sender-chan {:event [:followers/count followers]
                              :client-id client-id})))

  ;; Follow publishers
  ([message :guard :follow-publishers]
  ; Persist publishers followed by a certain user, respond with the complete follow list
  (let [user-id (:user-id message)
        org-slug (:org-slug message)
        publisher-uuids (:publisher-uuids message)]
    (timbre/info "Follow publishers request from:" user-id  "on:" org-slug "for:" publisher-uuids)
    (persist :follow :publishers user-id org-slug publisher-uuids)
    (let [follow-item (follow/retrieve user-id org-slug)]
      (>!! watcher/watcher-chan {:send true
                                 :watch-id (str org-slug "-" user-id)
                                 :event :follow/list
                                 :payload {:org-slug org-slug
                                           :unfollow-board-uuids (:unfollow-board-uuids follow-item)
                                           :follow-publisher-uuids (:follow-publisher-uuids follow-item)}}))))

  ;; Follow boards
  ([message :guard :unfollow-boards]
  ; Persist boards followed by a certain user, respond with the complete follow list
  (let [user-id (:user-id message)
        org-slug (:org-slug message)
        board-uuids (:board-uuids message)]
    (timbre/info "Follow boards request from:" user-id  "on:" org-slug "for:" board-uuids)
    (persist :unfollow :boards user-id org-slug board-uuids)
    (let [follow-item (follow/retrieve user-id org-slug)]
      (>!! watcher/watcher-chan {:send true
                                 :watch-id (str org-slug "-" user-id)
                                 :event :follow/list
                                 :payload {:org-slug org-slug
                                           :unfollow-board-uuids (:unfollow-board-uuids follow-item)
                                           :follow-publisher-uuids (:follow-publisher-uuids follow-item)}}))))

  ;; Follow publisher
  ([message :guard :follow-publisher]
  ; Persist that a container received a new item at a specific time
  (let [user-id (:user-id message)
        org-slug (:org-slug message)
        publisher-uuid (:publisher-uuid message)]
    (timbre/info "Follow request from:" user-id  "on:" org-slug
                                           "for:" publisher-uuid)
    (persist :follow :publisher user-id org-slug publisher-uuid)
    (let [follow-item (follow/retrieve user-id org-slug)]
      (>!! watcher/watcher-chan {:send true
                                 :watch-id (str org-slug "-" user-id)
                                 :event :follow/list
                                 :payload {:org-slug org-slug
                                           :unfollow-board-uuids (:unfollow-board-uuids follow-item)
                                           :follow-publisher-uuids (:follow-publisher-uuids follow-item)}}))))

  ;; Follow board
  ([message :guard :follow-board]
  ; Persist that a container received a new item at a specific time
  (let [user-id (:user-id message)
        org-slug (:org-slug message)
        board-uuid (:board-uuid message)]
    (timbre/info "Follow board request from:" user-id  "on:" org-slug "for:" board-uuid)
    (persist :follow :board user-id org-slug board-uuid)
    (let [follow-item (follow/retrieve user-id org-slug)]
      (>!! watcher/watcher-chan {:send true
                                 :watch-id (str org-slug "-" user-id)
                                 :event :follow/list
                                 :payload {:org-slug org-slug
                                           :unfollow-board-uuids (:unfollow-board-uuids follow-item)
                                           :follow-publisher-uuids (:follow-publisher-uuids follow-item)}}))))

  ;; Unfollow publisher
  ([message :guard :unfollow-publisher]
  ; Persist that a container received a new item at a specific time
  (let [user-id (:user-id message)
        org-slug (:org-slug message)
        publisher-uuid (:publisher-uuid message)]
    (timbre/info "Unfollow request from:" user-id  "on:" org-slug "for:" publisher-uuid)
    (persist :unfollow :publisher user-id org-slug publisher-uuid)
    (let [follow-item (follow/retrieve user-id org-slug)]
      (>!! watcher/watcher-chan {:send true
                                 :watch-id (str org-slug "-" user-id)
                                 :event :follow/list
                                 :payload {:org-slug org-slug
                                           :unfollow-board-uuids (:unfollow-board-uuids follow-item)
                                           :follow-publisher-uuids (:follow-publisher-uuids follow-item)}}))))

  ;; Unfollow board
  ([message :guard :unfollow-board]
  ; Persist that a container received a new item at a specific time
  (let [user-id (:user-id message)
        org-slug (:org-slug message)
        board-uuid (:board-uuid message)]
    (timbre/info "Unfollow board request from:" user-id  "on:" org-slug "for:" board-uuid)
    (persist :unfollow :board user-id org-slug board-uuid)
    (let [follow-item (follow/retrieve user-id org-slug)]
      (>!! watcher/watcher-chan {:send true
                                 :watch-id (str org-slug "-" user-id)
                                 :event :follow/list
                                 :payload {:org-slug org-slug
                                           :unfollow-board-uuids (:unfollow-board-uuids follow-item)
                                           :follow-publisher-uuids (:follow-publisher-uuids follow-item)}}))))

  ([message]
  (timbre/warn "Unknown request in persistence channel" message)))

;; ----- Persistence event loop -----

(defn persistence-loop []
  (reset! persistence-go true)
  (timbre/info "Starting persistence...")
  (async/go (while @persistence-go
    (timbre/debug "Persistence waiting...")
    (let [message (<! persistence-chan)]
      (timbre/debug "Processing message on persistence channel..." message)
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
