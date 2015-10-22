(ns asterion.server
  (:import [java.util UUID])
  (:require [clojure.java.io :as io]
            [clj-jgit.porcelain :as git]
            [clj-jgit.util :as git-util]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as response]
            [asterion.project :as project]))

(defn uuid []
  (str (UUID/randomUUID)))

(defn parse-url [url]
  (let [repo-name (git-util/name-from-uri url)
        dir (io/file "tmp" (uuid) repo-name)
        repo (git/git-clone-full url (.getPath dir))]
    (project/depgraph (project/parse-project (io/file dir "project.clj")))))

(defn repo-handler [url]
  (try
    {:status 200
     :headers {"Content-Type" "application/edn"}
     :body (pr-str {:graph (parse-url url)})}
    (catch Exception e
      {:status 500
       :headers {"Content-Type" "application/edn"}
       :body (pr-str {:error e})})))

(defroutes app
  (GET "/" _ (response/file-response "resources/public/index.html"))
  (GET "/repo" {{url :url} :params} (repo-handler url))
  (route/not-found "<h1>Page not found</h1>"))
