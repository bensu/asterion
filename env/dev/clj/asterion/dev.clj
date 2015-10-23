(ns asterion.dev
  (:require [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [com.stuartsierra.component :as component]
            [asterion.server :as server]))

;; ======================================================================  
;; Reloaded Workflow

(def system nil)

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system (constantly server/new-system)))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system component/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
    (fn [s] (when s (component/stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'asterion.dev/go))
