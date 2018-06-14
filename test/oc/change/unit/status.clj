(ns oc.change.unit.status
  (:require [midje.sweet :refer :all]
            [oc.change.async.persistence :as pers]
            [oc.change.resources.seen :as seen]))

(def container-1 "cccc-cccc-1111")
(def container-2 "cccc-cccc-2222")
(def item-1 "eeee-eeee-1111")
(def item-2 "eeee-eeee-3333")
(def item-3 "eeee-eeee-3333")

(defn- change-for [container item timestamp]
  {:container-id container
   :item-id item
   :change-at (str timestamp)})

(defn- seen-for [container item timestamp]
  {:container-id container
   :item-id item
   :seen-at (str timestamp)})

(facts 

  (fact "a container with no change has an empty status"
    (pers/status-for [container-1] [] []) => [{:container-id container-1 :unseen []}])

  (fact "a change that's never been seen is included in status"
    (pers/status-for [container-1] [(change-for container-1 item-1 1)] []) =>
      [{:container-id container-1 :unseen [item-1]}])

  (fact "a change that's not asked about is NOT included in status"
    (pers/status-for [container-1] [(change-for container-2 item-1 1)] []) =>
      [{:container-id container-1 :unseen []}])

  (fact "a change that's been explicitly seen is NOT included in status"
    (pers/status-for [container-1] [(change-for container-1 item-1 1)] [(seen-for container-1 item-1 2)]) =>
      [{:container-id container-1 :unseen []}]
    ;; This 2nd case of a change seen before it happened isn't possible in theory, but including to be safe
    (pers/status-for [container-1] [(change-for container-1 item-1 2)] [(seen-for container-1 item-1 1)]) =>
      [{:container-id container-1 :unseen []}])

  (future-fact "a container being seen AFTER a change means the item is seen and is NOT included in status"
    (pers/status-for [container-1] [(change-for container-1 item-1 1)] [(seen-for container-1 seen/entire-container 2)]) =>
      [{:container-id container-1 :unseen []}])

  (fact "a container being seen BEFORE a change means the item is NOT seen and is included in status"
    (pers/status-for [container-1] [(change-for container-1 item-1 2)] [(seen-for container-1 seen/entire-container 1)]) =>
      [{:container-id container-1 :unseen [item-1]}])

  (fact "multiple changes in a single container at the same time returns the correct status"
    (pers/status-for [container-1] [(change-for container-1 item-1 1) (change-for container-1 item-2 2)] []) =>
      [{:container-id container-1 :unseen [item-1 item-2]}])

  (fact "asking about multiple containers at the same time returns the correct status"
    (pers/status-for [container-1 container-2] [] []) => [{:container-id container-1 :unseen []}
                                                          {:container-id container-2 :unseen []}]
    (pers/status-for [container-1 container-2] [(change-for container-1 item-1 1)] []) => 
                                                         [{:container-id container-1 :unseen [item-1]}
                                                          {:container-id container-2 :unseen []}]
    (pers/status-for [container-1 container-2] [(change-for container-2 item-1 1)] []) => 
                                                         [{:container-id container-1 :unseen []}
                                                          {:container-id container-2 :unseen [item-1]}]
    (pers/status-for [container-1 container-2] [(change-for container-1 item-1 1)] [(seen-for container-2 item-1 2)]) => 
                                                         [{:container-id container-1 :unseen [item-1]}
                                                          {:container-id container-2 :unseen []}]
    (pers/status-for [container-1 container-2] [(change-for container-1 item-1 1)] [(seen-for container-1 item-1 2)]) => 
                                                         [{:container-id container-1 :unseen []}
                                                          {:container-id container-2 :unseen []}])

  (future-fact "complex multi-item, multi-container scenario returns the correct status")

  )