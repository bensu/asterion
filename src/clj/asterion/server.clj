(ns asterion.server
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [asterion.project :as project]))

;; ====================================================================== 
;; Utils

(defn ->url [user repo]
  (str "https://github.com/" user "/" repo ".git"))

(defn error-response [error]
  {:status 500
   :headers {"Content-Type" "application/edn"}
   :body (pr-str error)})

(defn ok-response [body]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str body)})

;; ====================================================================== 
;; Cache

(def cache-root "cached")

(defn cache-path [user repo]
  (str (str/join "/" [cache-root user repo]) ".edn") )

(defn spit-graph
  "Writes to disk the results"
  [{:keys [user repo graph] :as data}]
  {:pre [(every? some? (vals data))]}
  (let [p (str "resources/public/" (cache-path user repo))
        parent (.getParentFile (io/file p))]
    ;; ensure directory 
    (when-not (.exists parent)
      (.mkdirs parent))
    (spit p data)
    p))

(defn cache!
  ([user repo]
   (cache! user repo ""))
  ([user repo subpath]
   (spit-graph {:user user
                :repo repo
                :graphs (project/parse-url (->url user repo) subpath)})))

(defn cached
  "Returns a path if the graph for the repo is in cache, otwherwise nil"
  [user repo]
  (when-let [c (some-> (cache-path user repo) io/resource io/file)]
    (when (.exists c)
      (.getPath c))))

(defn repo-handler [user repo]
  (try
    (if-let [cache-path (cached user repo)]
      (response/file-response cache-path)
      (let [graphs (project/parse-url (->url user repo))]
        (if (contains? graphs :error)
          (error-response graphs)
          (do
            (future (spit-graph {:user user :repo repo :graphs graphs}))
            (ok-response {:graphs graphs})))))
    (catch Exception e
      (error-response {:error e}))))

(defroutes app-routes 
  (GET "/" [] (response/file-response "public/index.html"))
  (HEAD "/" [] "")
  (GET "/repo/:user/:repo" [user repo] 
    (if (and (string? user) (string? repo))
      (repo-handler user repo)
      (error-response (str "Bad params: " user repo))))
  (route/resources "/")
  (route/files "/")
  (route/not-found "<h1>404 - Page not found</h1>"))

(def app-handler
  (-> app-routes
    params/wrap-params))

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
