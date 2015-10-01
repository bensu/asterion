(ns constable.project
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [cljs.tools.reader.edn :as edn]))

(enable-console-print!)

(defn re-pos [re s]
  (let [re (js/RegExp. (.-source re) "g")]
    (loop [res {}]
      (if-let [m (.exec re s)]
        (recur (assoc res (.-index m) (first m)))
        res))))

(defn extract-positions [project-string re]
  (->> project-string
    (re-pos re)
    keys))

(defn extract-exp-at [project-string re pos]
  (-> project-string
    (subs pos)
    (str/replace re "" )
    edn/read-string))

(defn normalize-builds [builds]
  (cond
    (map? builds) (mapv (fn [[k v]] (assoc v :id (name k))) builds) 
    (vector? builds) builds 
    :else (throw (js/Error. "Builds should be maps or vectors"))))

(defn parse [project-string]
  (let [re #":cljsbuild"
        build-srcs (->> (extract-positions project-string re)
                     (map (partial extract-exp-at project-string re))
                     ;; First :cljsbuild occurrence (mulitple
                     ;; profiles,multiple builds) 
                     first
                     :builds
                     normalize-builds
                     ;; ;; First build out all the possible builds
                     first
                     :source-paths
                     set)
        re #":source-paths"
        main-srcs (->> (extract-positions project-string re)
                    first
                    (extract-exp-at project-string re)
                    set)]
    (set/union main-srcs build-srcs)))