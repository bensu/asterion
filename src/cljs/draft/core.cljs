(ns draft.core
  (:require [cljs.nodejs :as node]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(enable-console-print!)

(def fs (node/require "fs"))

(defn main-page [data]
  (om/component
    (dom/div nil
      (if (nil? (:files data))
        (dom/h1 nil "Loading...")
        (apply dom/ul nil
          (map (partial dom/li nil) (:files data)))))))

(defonce app-state (atom {}))

(defn mount! []
  (om/root main-page app-state {:target  (.getElementById js/document "app")}))

(fs.readdir "/"
  (fn [error files]
    (reset! app-state {:files files})
    (mount!)))
