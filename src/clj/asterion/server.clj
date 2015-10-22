(ns asterion.server
  (:import [java.util UUID])
  (:require [clojure.java.io :as io]
            [clj-jgit.porcelain :as git]
            [clj-jgit.util :as git-util]
            [asterion.project :as project]))

(defn uuid []
  (str (UUID/randomUUID)))

(defn parse-url [url]
  (let [repo-name (git-util/name-from-uri url)
        dir (io/file "tmp" (uuid) repo-name)
        repo (git/git-clone-full url (.getPath dir))]
    (project/depgraph (project/parse-project (io/file dir "project.clj")))))
