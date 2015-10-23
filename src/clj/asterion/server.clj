(ns asterion.server
  (:import [java.util UUID]
           [org.apache.commons.io FileUtils]
           [org.eclipse.jgit.api.errors TransportException
                                        InvalidRemoteException])
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clj-jgit.porcelain :as git]
            [clj-jgit.util :as git-util]
            [asterion.project :as project]))

(defn uuid []
  (str (UUID/randomUUID)))

(defn parse-url
  "Given a git url (string) it will fetch the repository and try to return
   a dependency graph for it. It may also return the following errors:

  - {:error :no-project-file}
  - {:error :project-not-found}
  - {:error :project-protected}
  - {:error #<Exception>} ;; unknown exception"
  [url]
  {:pre [(string? url)]}
  (let [repo-name (git-util/name-from-uri url)
        dir (io/file "tmp" (str (uuid) repo-name))]
    (try
      (git/git-clone-full url (.getPath dir))
      (project/depgraph (project/parse-project dir))
      (catch InvalidRemoteException _
        {:error :project/not-found})
      (catch TransportException _
        {:error :project/protected})
      (catch Exception e
        {:error e})
      (finally (future (FileUtils/deleteDirectory dir))))))

(defn error-response [error]
  {:status 500
   :headers {"Content-Type" "application/edn"}
   :body (pr-str error)})

(defn ok-response [body]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str body)})

(defn repo-handler [url]
  (try
    (let [graph (parse-url url)]
      (if (contains? graph :error)
        (error-response graph)
        (ok-response {:graph graph})))
    (catch Exception e
      (error-response {:error e}))))

(defroutes app-routes 
  (GET "/" _ (response/file-response "resources/public/index.html"))
  (GET "/repo/:user/:repo" [user repo] 
    (if (and (string? user) (string? repo))
      (repo-handler (str "https://github.com/" user "/" repo ".git"))
      (error-response (str "Bad params: " user repo))))
  (route/resources "/")
  (route/files "/")
  (route/not-found "<h1>404 - Page not found</h1>"))

(def app-handler (params/wrap-params app-routes))

(defn start-jetty [handler port]
  (jetty/run-jetty handler {:port (Integer. port) :join? false}))

(defrecord Server [port jetty]
  component/Lifecycle
  (start [component]
    (println "Start server at port " port)
    (assoc component :jetty (start-jetty app-handler port)))
  (stop [component]
    (println "Stop server")
    (when jetty 
      (.stop jetty))
    component))

(def new-system (Server. 3000 nil))
