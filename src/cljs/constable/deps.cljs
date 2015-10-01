(ns constable.deps 
  (:require [clojure.set :as set]
            [clojure.tools.namespace.file :as file]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.dependency :as dep]))

(def path (js/require "path"))

(def fs (js/require "fs"))

(defn file->folder [p]
  (path.dirname p))

(defn join-paths [& args]
  (apply path.join args))

(defn list-files [dir]
  (mapv (partial join-paths dir) (into-array (fs.readdirSync dir))))

(defn dir? [d]
  (try
    (.isDirectory (fs.lstatSync d))
    (catch js/Object _
      false)))

(defn file-name [p]
  (.-name (path.parse p)))

(defn list-dirs [dir]
  (vec (filter dir? (list-files dir))))

(defn read-file [file]
  (fs.readFileSync file "utf8"))

(defn platform->ext [platform]
  {:pre [(keyword platform)]}
  (get {:clj find/clj
        :cljs find/cljs
        :cljc (update find/clj :extensions (partial cons ".cljs"))}
    platform))

(defn depgraph
  ([srcs] (depgraph :cljs srcs))
  ([platform srcs]
   (let [exts (platform->ext platform) 
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
     {:edges json-edges :nodes json-nodes})))