(ns graph.util
  (:require [clojure.set :as set]))

(defn useful-nodes [g]
  (loop [useful-nodes #{}
         nodes (keys g)]
    (if-let [node (first nodes)]
      (let [edges (set (get g node))]
        (recur (cond-> (set/union useful-nodes edges)
                 (not (empty? edges)) (conj node))
          (rest nodes)))
      useful-nodes)))

(defn clear-disj-nodes [g]
  (let [disj-nodes (set/difference (set (keys g))
                                   (useful-nodes g))]
    (apply dissoc g disj-nodes)))
