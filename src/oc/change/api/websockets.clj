(ns oc.change.api.websockets
  "WebSocket server handler."
  (:require [clojure.core.async :as async :refer (>!! <!)]
            [taoensso.sente :as sente]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (defroutes GET POST)]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
            [oc.lib.async.watcher :as watcher]
            [oc.change.config :as c]
            [oc.change.async.persistence :as persistence]))

;; ----- core.async -----

(defonce sender-go (atom true))

;; ----- Sente server setup -----

;; https://github.com/ptaoussanis/sente#on-the-server-clojure-side

(reset! sente/debug-mode?_ (not c/prod?))

(let [{:keys [ch-recv send-fn connected-uids ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket-server! (get-sch-adapter) 
        {:packer :edn
         :user-id-fn (fn [ring-req] (:client-id ring-req)) ; use the client id as the user id
         :csrf-token-fn (fn [ring-req] (:client-id ring-req))
         :handshake-data-fn (fn [ring-req] (timbre/debug "handshake-data-fn") {:carrot :party})})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def connected-uids connected-uids)) ; Read-only atom of uids with Sente WebSocket connections

;; Uncomment to watch the connection atom for changes
; (add-watch connected-uids :connected-uids
;   (fn [_ _ old new]
;     (when (not= old new)
;       (timbre/debug "[websocket]: atom update" new))))

;; ----- Sente incoming event handling -----

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id) ; Dispatch on event-id

(defn- event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (timbre/trace "[websocket]" event id ?data)
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  ;; Default/fallback case (no other matching handler)
  :default
  
  [{:keys [event id ?reply-fn]}]
  (timbre/debug "[websocket] unhandled event" event "for" id)
  (when ?reply-fn
    (?reply-fn {:umatched-event-as-echoed-from-from-server event})))

(defmethod -event-msg-handler
  :chsk/handshake
  
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (timbre/trace "[websocket] chsk/handshake" event id ?data)
  (when ?reply-fn
    (?reply-fn {:umatched-event-as-echoed-from-from-server event})))

(defmethod -event-msg-handler
  :chsk/ws-ping
  [_]
  (timbre/trace "[websocket] ping"))

(defmethod -event-msg-handler
  ;; Client connected
  :chsk/uidport-open
  
  [{:as ev-msg :keys [event id ring-req]}]
  (let [user-id (-> ring-req :params :user-id)
        client-id (-> ring-req :params :client-id)]
    (timbre/debug "[websocket] chsk/uidport-open by:" user-id "/" client-id)))

(defmethod -event-msg-handler
  ;; Client disconnected
  :chsk/uidport-close
  
  [{:as ev-msg :keys [event id ring-req]}]
  (let [user-id (-> ring-req :params :user-id)
        client-id (-> ring-req :params :client-id)]
    (timbre/info "[websocket] container/uidport-close by:" user-id "/" client-id)))


(defmethod -event-msg-handler
  :container/watch
  
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [user-id (-> ring-req :params :user-id)
        client-id (-> ring-req :params :client-id)
        container-ids ?data]
    (timbre/info "[websocket] container/watch for:" container-ids "by:" user-id "/" client-id)
    (>!! persistence/persistence-chan {:status true
                                       :container-ids container-ids
                                       :user-id user-id
                                       :client-id client-id})
    (doseq [container-id container-ids]
      (>!! watcher/watcher-chan {:watch true
                                 :watch-id container-id
                                 :client-id client-id}))))

(defmethod -event-msg-handler
  :container/seen
  
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [user-id (-> ring-req :params :user-id)
        client-id (-> ring-req :params :client-id)
        container-id (:container-id ?data)
        seen-at (:seen-at ?data)]
    (timbre/info "[websocket] container/seen for:" container-id "at:" seen-at "by:" user-id "/" client-id)
    (>!! persistence/persistence-chan {:seen true :user-id user-id :container-id container-id :seen-at seen-at})))

;; ----- Sente router event loop (incoming from Sente/WebSocket) -----

(defonce router_ (atom nil))

(defn- stop-router! []
  (when-let [stop-fn @router_]
    (stop-fn)))

(defn- start-router! []
  (stop-router!)
  (reset! router_
    (sente/start-server-chsk-router!
      ch-chsk event-msg-handler)))

;; ----- Sender event loop (outgoing to Sente/WebSocket) -----

(defn sender-loop []
  (reset! sender-go true)
  (timbre/info "Starting sender...")
  (async/go (while @sender-go
    (timbre/debug "Sender waiting...")
    (let [message (<! watcher/sender-chan)]
      (timbre/debug "Processing message on sender channel...")
      (if (:stop message)
        (do (reset! sender-go false) (timbre/info "Sender stopped."))
        (async/thread
          (try
            (timbre/info "Sender received:" message)
            (let [event (:event message)
                  client-id (or (:client-id message) (:id message))]
              (timbre/info "[websocket] sending:" (first event) "to:" client-id)
              (chsk-send! client-id event))
            (catch Exception e
              (timbre/error e)))))))))

;; ----- Ring routes -----

(defn routes [sys]
  (compojure/routes
    (GET "/change-socket/user/:user-id" req (ring-ajax-get-or-ws-handshake req))
    (POST "/change-socket/user/:user-id" req (ring-ajax-get-or-ws-handshake req))))

;; ----- Component start/stop -----

(defn start
  "Start the incoming WebSocket frame router and the core.async loop for sending outgoing WebSocket frames."
  []
  (start-router!)
  (sender-loop))

(defn stop
  "Stop the incoming WebSocket frame router and the core.async loop for sending outgoing WebSocket frames."
  []
  (timbre/info "Stopping incoming websocket router...")
  (stop-router!)
  (timbre/info "Router stopped.")
  (when @sender-go
    (timbre/info "Stopping sender...")
    (>!! watcher/sender-chan {:stop true})))

;; ----- REPL usage -----

(comment

  ;; WebSocket REPL server

  (require '[oc.change.components :as components] :reload)
  (require '[oc.change.app :as app] :reload)
  (require '[oc.change.api.websockets] :reload)
  (require '[oc.change.async.persistence] :reload)
  (go)

  ;; WebSocket REPL client

  (require '[http.async.client :as http])
  (require '[oc.lib.time :as oc-time])
  (require '[oc.change.resources.container :as container])
  
  (def ws-conn (atom nil))

  (def url "ws://localhost:3006/change-socket/user/1234-abcd-1234?client-id=1")

  (defn on-open [ws]
    (println "Connected to WebSocket."))
  
  (defn on-close [ws code reason]
    (println "Connection to WebSocket closed.\n"
           (format "[%s] %s" code reason)))

  (defn on-error [ws e]
    (println "ERROR:" e))

  (defn handle-message [ws msg]
    (prn "got message:" msg))

  (defn message-stamp
    "Return a 6 character fragment from a UUID e.g. 51ab4c86"
    []
    (s/join "" (take 2 (rest (s/split (str (java.util.UUID/randomUUID)) #"-")))))
  
  (defn send-message [msg-type msg-body]
    (println "Sending...")
    (http/send @ws-conn :text (str "+" (pr-str [[msg-type msg-body] (message-stamp)])))
    (println "Sent..."))

  (defn connect-client []
    (future (with-open [client (http/create-client)]
      (let [ws (http/websocket client
                               url
                               :open  on-open
                               :close on-close
                               :error on-error
                               :text handle-message)]
        ; this loop-recur is here as a placeholder to keep the process
        ; from ending, so that the message-handling function will continue to
        ; print messages to STDOUT
        (reset! ws-conn ws)
        (loop [] 
          (if @ws-conn
            (recur)
            (println "Client stopped!")))))))

  (connect-client)

  (send-message :container/seen {:container-id "1a1b-2a2b-3a3b" :seen-at (oc-time/current-timestamp)})

  (send-message :container/watch ["1a1b-2a2b-3a3b"])

  ;; Test
  ; 1 - change, never seen
  ; 2 - seen, never changed
  ; 3 - change, then seen
  ; 4 - seen, then changed
  ; 5 - never seen or change
  (container/change! "1111-1111-1111" (oc-time/current-timestamp))
  (send-message :container/seen {:container-id "2222-2222-2222" :seen-at (oc-time/current-timestamp)})
  (container/change! "3333-3333-3333" (oc-time/current-timestamp))
  (send-message :container/seen {:container-id "3333-3333-3333" :seen-at (oc-time/current-timestamp)})
  (send-message :container/seen {:container-id "4444-4444-4444" :seen-at (oc-time/current-timestamp)})
  (container/change! "4444-4444-4444" (oc-time/current-timestamp))

  (send-message :container/watch ["1111-1111-1111" "2222-2222-2222" "3333-3333-3333" "4444-4444-4444" "5555-5555-5555"])

  (reset! ws-conn nil) ; stop the client

  )