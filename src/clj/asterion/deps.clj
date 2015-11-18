(ns asterion.deps
  (:import [java.util UUID]
           [org.apache.commons.io FileUtils]
           [org.eclipse.jgit.api.errors InvalidRemoteException]
           [org.eclipse.jgit.errors TransportException])
  (:require [clojure.java.io :as io]
            [clj-jgit.porcelain :as git]
            [clj-jgit.util :as git-util]
            [asterion.deps.clojure :as clj]
            [asterion.deps.javascript :as js]))

;; ======================================================================
;; Utils

(defn uuid []
  (str (UUID/randomUUID)))

(defn project-type [dir subpath]
  (letfn [(file-exists? [f]
            (.exists (io/file dir subpath f)))]
    (cond
      (file-exists? "package.json") :js
      (file-exists? "project.clj") :clj
      :else nil)))

(defmulti parse-repo project-type)

(defmethod parse-repo :default [_ _] {:error :project/cant-find-sources})

(defmethod parse-repo :clj
  [dir subpath]
  (clj/parse-repo dir subpath))

(defmethod parse-repo :js
  [dir subpath]
  (js/parse-repo dir subpath))

(defn parse-url
  "Given a git url (string) it will fetch the repository and try to return
   a dependency graph for it. It may also return the following errors:

  - {:error :no-project-file}
  - {:error :project-not-found}
  - {:error :project-protected}
  - {:error #<Exception>} ;; unknown exception"
  ([url] (parse-url url ""))
  ([url subpath]
   {:pre [(string? url)]}
   (let [repo-name (git-util/name-from-uri url)
         dir (io/file "tmp" (str (uuid) repo-name))]
     (try
       (git/git-clone-full url (.getPath dir))
       (parse-repo dir subpath)
       (catch InvalidRemoteException _
         {:error :project/not-found})
       (catch TransportException _
         {:error :project/protected})
       (catch Exception e
         {:error e})
       (finally (future (FileUtils/deleteDirectory dir)))))))
