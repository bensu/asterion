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
