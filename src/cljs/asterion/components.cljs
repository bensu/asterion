(ns asterion.components
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

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
               :target "_blank"
               :href href}
     label)))
