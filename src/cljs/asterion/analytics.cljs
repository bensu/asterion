(ns asterion.analytics)

(defn repo-event! [event data]
  (js/ga "send" "event" "Repositories" event data))

(defn nav-event! [event data]
  (js/ga "send" "event" "Nav" event data))
