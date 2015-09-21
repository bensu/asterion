(ns draft.deps 
  "Generate a namespace dependency graph as an svg file"
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.namespace.file :as ns-file]
            [clojure.tools.namespace.track :as ns-track]
            [clojure.tools.namespace.find :as ns-find]
            [clojure.tools.namespace.dependency :as ns-dep]
            [clojure.data.json :as json]))

(defn ns->group-name [nss] 
  (first (str/split (name nss) #"\.")))

(defn nodes->colors [nodes]
  (loop [colors {}
         nds nodes]
    (if-let [n (first nds)]
      (let [group (first (str/split (name n) #"\."))]
        (recur (if (contains? colors group)
                 colors
                 (assoc colors group group))
               (rest nds)))
      colors)))

(defn depgraph
  "Generate a namespace dependency graph as svg file"
  [project]
  (let [json-file (io/file (get-in project [:deps :to])
                    (str (:name project) ".json"))
        exts (update ns-find/clj :extensions (partial cons ".cljs"))
        source-fs (apply set/union
                    (->> (project :source-paths)
                      (map (comp #(ns-find/find-sources-in-dir % exts)
                             io/file))))
        tracker (ns-file/add-files {} source-fs)
        dep-graph (tracker ::ns-track/deps)
        ns-names (set (map (comp second ns-file/read-file-ns-decl) source-fs))
        part-of-project? (partial contains? ns-names)
        nodes (filter part-of-project? (reverse (ns-dep/topo-sort dep-graph)))
        colors (nodes->colors nodes)
        edges (->> nodes
                (mapcat #(->> (filter part-of-project?
                                (ns-dep/immediate-dependencies dep-graph %))
                           (map (partial vector %)))))
        ;; comp reverse vector
        idx (into {} (map-indexed (fn [i n] [n i]) nodes))
        json-nodes (mapv (fn [[n i]] {:name (str n)}) (sort-by second idx))
        json-edges (map (fn [[from to]] {:source from :target to}) edges)]
    (with-open [^java.io.Writer w (io/writer json-file)]
      (json/write {:edges json-edges :nodes json-nodes} w))
    json-file))

(comment
  (def project {:source-paths ["/home/carlos/Komunike/login/src"]
                :name "Komunike"
                :deps {:to "resources/public"}}))
