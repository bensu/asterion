(ns asterion.deps.javascript
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.data.json :as json]
            [graph.util :as graph]))

(defn maybe-conj [coll v]
  (cond-> coll
      (some? v) (conj v)))

(defn rename-file [f]
  (str/replace f #"/" "."))

(defn module-name [n]
  (last (str/split n #"/")))

(defn normalize-graph
  "Transforms all the module names into full paths
   and removes the outside dependencies"
  [g]
  (let [node-names (set (keys g))
        module-names (zipmap (map module-name node-names)
                       node-names)
        in-project #(or (node-names %) (get module-names %))]
    (zipmap node-names
            (map #(remove nil? (mapv in-project %)) (vals g)))))


(defn parse-repo
  [dir subpath]
  (with-open [rdr (io/reader (io/file dir subpath "package.json"))]
    (let [package-json (json/read rdr)
          srcs (-> #{}
                 (set/union (set (get package-json "files")) #{"src" "lib"})
                 (maybe-conj (get-in package-json ["directories" "lib"])))
          srcs-with-root (->> srcs
                           (map (partial io/file dir subpath))
                           (filter #(.exists %))
                           (map #(.getPath %)))]
      (assert (every? some? srcs-with-root) "Paths can't be nil")
      (let [g (-> (apply sh "madge" "-j" srcs-with-root)
                :out
                json/read-str
                normalize-graph
                graph/clear-disj-nodes)
            node-names (keys g)]
        {"js" {:nodes (mapv (comp (partial hash-map :name) rename-file)
                            node-names)
               :edges (->> node-names
                        (mapcat (fn [n]
                                  (->> (get g n)
                                    (map (fn [e]
                                           {:source (rename-file n)
                                            :target (rename-file e)}))
                                    (remove nil?))))
                        vec)}}))))
