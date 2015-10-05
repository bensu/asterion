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

(defn parse-name [project-string]
  (let [re #"defproject"]
    (->> re
      (extract-positions project-string)
      first
      (extract-exp-at project-string re)
      name)))

;; TODO: move to test
(comment
  (.log js/console (parse-name "(defproject komunike \"0.9.0-SNAPSHOT)"))
  (.log js/console (parse-name "(defproject org.omcljs/om \"0.9.0-SNAPSHOT)")))

(defn parse-main-srcs [project-string]
  {:pre [(string? project-string)]
   :post [(set? %) (every? string? %)]}
  (let [re #":source-paths"]
    (->> (extract-positions project-string re)
      first
      (extract-exp-at project-string re)
      set)))

(defn parse-builds-srcs [project-string]
  (let [re #":cljsbuild"]
    (->> (extract-positions project-string re)
      (map (partial extract-exp-at project-string re))
      ;; First :cljsbuild occurrence (mulitple
      ;; profiles,multiple builds) 
      first
      :builds
      normalize-builds
      ;; ;; First build out all the possible builds
      first
      :source-paths
      set)))

(defn parse [project-string]
  (set/union (parse-main-srcs project-string) 
             (parse-builds-srcs project-string)))
