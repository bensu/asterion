(ns asterion.components
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [asterion.analytics :refer [nav-event!]]))

(defn icon-button [{:keys [icon-class title]} owner {:keys [click-fn]}]
  (om/component
    (dom/i #js {:className (str icon-class " fa clickable")
                :title title 
                :onClick (if (fn? click-fn)
                           click-fn
                           identity)})))

(defn link
  ([href] (link href href))
  ([href label] (link label href ""))
  ([href label class]
   (dom/a #js {:className (str "file-item__text " class)
               :onClick (fn [_]
                          (nav-event! "link-out" href))
               :target "_blank"
               :href href}
     label)))

(def form-url
  "https://docs.google.com/forms/d/19FW2TpxN3FmtJ6drU5N6Ia8g3v_yhr1VuVq5sa74dZ4/viewform")

(defn forms-link [label]
  (dom/a #js {:className (str "file-item__text file--activate")
              :onClick (fn [_]
                         (nav-event! "link-out" "to-form"))
              :target "_blank"
              :href form-url}
    label))
