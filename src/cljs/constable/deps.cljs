(ns constable.deps 
  (:require [clojure.set :as set]
            [clojure.tools.namespace.file :as file]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.dependency :as dep]))

;; ======================================================================  
;; Helpers

(defn file->ns-name [f]
  (-> f file/read-file-ns-decl rest first))

;; ====================================================================== 
;; Dependency graph 

(defn platform->ext [platform]
  {:pre [(keyword platform)]}
  (get {:clj find/clj
        :cljs find/cljs
        :cljc (update find/clj :extensions (partial cons ".cljs"))}
    platform
    find/clj))

(defn depgraph
  "Constructs a graph - {:nodes [{:name foo.bar}]
                         :edges [{:source foo.bar :target foo.baz}]}
  from a set of source-paths."
  ([srcs] (depgraph nil srcs))
  ([platform srcs]
   (let [exts (platform->ext platform) 
         source-fs (apply set/union
                     (map #(find/find-sources-in-dir % exts) srcs))
         dep-graph ((file/add-files {} source-fs) ::track/deps)
         ns-names (set (map (comp second file/read-file-ns-decl) source-fs))
         part-of-project? (partial contains? ns-names)
         nodes (filter part-of-project? (reverse (dep/topo-sort dep-graph)))
         edges (->> nodes
                 (mapcat #(->> (filter part-of-project?
                                 (dep/immediate-dependencies dep-graph %))
                            (map (partial vector %)))))]
     {:edges (mapv (fn [[from to]] {:source from :target to}) edges)
      :nodes (->> nodes
               (map-indexed (fn [i n] [n i]))
               (into {})
               (sort-by second)
               (mapv (fn [[n i]] {:name (str n)})))})))

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
