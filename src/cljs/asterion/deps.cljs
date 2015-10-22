(ns asterion.deps 
  (:require [clojure.set :as set]))

;; ======================================================================  
;; Helpers

(defn valid-graph? [graph]
  (not (empty? (:nodes graph))))

(defn filter-graph
  "Removes from the graph the nodes and edges that match the namespaces in ns"
  [graph ns]
  (letfn [(starts-with-any? [s]
            (->> ns
              (map #(re-find (js/RegExp. %) s))
              (some some?)))
          (node-matches? [node]
            (starts-with-any? (name (:name node))))
          (edge-matches? [edge]
            (->> (vals (select-keys edge [:target :source]))
              (map (comp starts-with-any? name))
              (some true?)))]
    (-> graph
      (update :nodes (comp vec (partial remove node-matches?)))
      (update :edges (comp vec (partial remove edge-matches?))))))

(defn highlight-graph
  "Adds :highlight true to the nodes in the graph that are also in highlighted"
  [graph highlighted]
  {:pre [(map? graph) (set? highlighted)]}
  (let [highlighted (set (map name highlighted))]
    (update graph :nodes
      (partial mapv #(assoc % :highlight (contains? highlighted (:name %)))))))
