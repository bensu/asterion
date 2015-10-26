(ns asterion.click
  "Provides a listener for click elements, click-outside"
  (:import [goog.events EventType]
           [goog.events Event])
  (:require [goog.dom :as gdom]
            [goog.events :as events]))

(defn- click-handler [in? sel f e]
  {:pre [(coll? sel) (fn? f)]}
  (let [t (.-target e)
        ff (if in? identity not)]
    (when (ff (some (partial gdom/getAncestorByClass t) sel))
      ;; We want to prevent the "in" element from handling it
      (when (ff true)
        (Event.stopPropagation e)
        (Event.preventDefault e))
      (f e)
      nil)))

(defn install! [in? sel f]
  (events/listen js/window EventType.CLICK
    (partial click-handler in? sel f) true))

(defn install-out!
 "Calls f with e whenever there is a click event e outside of the
  elements matched by the selectors in sel. Currently only classes are
  supported as selectors.
   Ex: sel = [\"some-class\" \"some-other-class\"]
   
       would match all clicks outside elements with those classes."
  [sel f]
  (install! false sel f))

(defn install-in!
  "Calls f with e whenever there is a click event e inside of the
  elements matched by the selectors in sel. Currently only classes are
  supported as selectors.
   Ex: sel = [\"some-class\" \"some-other-class\"]
   
       would match all clicks outside elements with those classes."
  [sel f]
  (install! true sel f))

(defn uninstall! [key]
  (events/unlistenByKey key))
