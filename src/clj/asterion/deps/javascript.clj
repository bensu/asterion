(ns asterion.deps.javascript
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.data.json :as json]))

(defn maybe-conj [coll v]
  (cond-> coll
      (some? v) (conj v)))

(defn rename-file [f]
  (str/replace f #"/" "."))

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
      (let [g (json/read-str (:out (apply sh "madge" "-j" srcs-with-root)))
            node-names (set (keys g))
            in-project? (partial contains? node-names)]
        {"js" {:nodes (mapv (comp (partial hash-map :name) rename-file)
                        node-names)
               :edges (->> node-names
                        (mapcat (fn [n]
                                  (->> (get g n)
                                    (map (fn [e]
                                           (when (in-project? e)
                                             {:source (rename-file n)
                                              :target (rename-file e)})))
                                    (remove nil?))))
                        vec)}}))))
