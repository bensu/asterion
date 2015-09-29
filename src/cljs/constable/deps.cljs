(ns constable.deps 
  (:require [clojure.set :as set]
            [clojure.tools.namespace.file :as file]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.dependency :as dep]))

(def path (js/require "path"))

(def fs (js/require "fs"))

(defn file->folder [p]
  (println p)
  (path.dirname p))

(defn list-files [dir]
  (fs.readdirSync dir))

(defn join-paths [& args]
  (apply path.join args))

(defn depgraph [srcs]
  {:pre [(seq? srcs)]}
  (let [exts (update find/clj :extensions (partial cons ".cljs"))
        source-fs (apply set/union
                    (map #(find/find-sources-in-dir % exts) srcs))
        tracker (file/add-files {} source-fs)
        dep-graph (tracker ::track/deps)
        ns-names (set (map (comp second file/read-file-ns-decl) source-fs))
        part-of-project? (partial contains? ns-names)
        nodes (filter part-of-project? (reverse (dep/topo-sort dep-graph)))
        edges (->> nodes
                (mapcat #(->> (filter part-of-project?
                                (dep/immediate-dependencies dep-graph %))
                           (map (partial vector %)))))
        idx (into {} (map-indexed (fn [i n] [n i]) nodes))
        json-nodes (mapv (fn [[n i]] {:name (str n)}) (sort-by second idx))
        json-edges (mapv (fn [[from to]] {:source from :target to}) edges)]
    {:edges json-edges :nodes json-nodes}))
