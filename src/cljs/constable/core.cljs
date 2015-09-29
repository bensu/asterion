(ns constable.core
  (:require [clojure.string :as str]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [constable.tree :as tree]
            [constable.deps :as deps]))

(enable-console-print!)

(defonce app-state (atom {:ns ""
                          :highlight ""
                          :dir nil 
                          :srcs []
                          :graph {}}))

(defn draw! [data]
  (tree/drawTree "#graph"
    (clj->js
      {:ns (str/split (:ns data) " ")
       :highlight (str/split (:highlight data) " ")})
    (clj->js (:graph data))))

(defn dir-item [item owner]
  (om/component
    (dom/li nil
      (dom/span nil (dom/input #js {:type "checkbox"
                                    :checked (:selected? item)
                                    :onClick (fn [e]
                                               (om/transact! item :selected? not))})
        (:name item)))))

(defn e->file [e]
  (aget (or (.. e -target -files)
            (.. e -target -value)
            (.. e -dataTransfer -files))
    0))

(defn select-project [data owner]
  (om/component
    (dom/div nil
      (dom/h1 nil "Select your project folder!")
      (dom/input #js {:type "file"
                      :onChange
                      (fn [e]
                        (.preventDefault e)
                        (let [f (e->file e) 
                              path (deps/file->folder (.-path f))]
                          (om/update! data :dir
                            {:root path 
                             :ls (->> (deps/list-files path)
                                   into-array 
                                   (mapv (fn [n] {:name n
                                                 :selected? false})))})))}))))

(defn explorer [data owner]
  (om/component
    (dom/div nil
      (dom/p nil (:root (:dir data)))
      (apply dom/ul nil
        (om/build-all dir-item (:ls (:dir data))))
      (dom/button
        #js {:onClick (fn [_]
                        (let [srcs (->> (:ls (:dir data))
                                     (filter :selected?)
                                     (map :name)
                                     (map (partial deps/join-paths (:root (:dir data)))))
                              graph (deps/depgraph srcs)]
                          (om/transact! data
                            #(assoc % :srcs srcs :graph graph :draw? true))))}
        "Explore!"))))

;; TODO: should show a loader
(defn graph [data owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
        (dom/div nil
          (dom/span #js {:className "controls"}
            "filter ns: "
            (dom/input #js {:type "text"
                            :value (:ns data)
                            :onKeyDown (fn [e]
                                         (when (= "Enter" (.-key e))
                                           (draw! data)))
                            :onChange (fn [e]
                                        (om/update! data :ns
                                          (.. e -target -value)))}))
          (dom/br #js {})
          (dom/span #js {:className "controls"}
            "highlight ns: "
            (dom/input #js {:type "text"
                            :value (:highlight data)
                            :onKeyDown (fn [e]
                                         (when (= "Enter" (.-key e))
                                           (draw! data)))
                            :onChange (fn [e]
                                        (om/update! data :highlight
                                          (.. e -target -value)))})))
        (dom/svg #js {:id "graph"}
          (dom/g #js {}))))
    om/IDidUpdate
    (did-update [_ pp ps]
      (when (and (not (empty? (:graph pp))) (:draw? pp))
        (om/update! data :draw? false)
        (draw! pp)))))

;; TODO: should be a multimethod
(defn main [data owner]
  (om/component
   (cond
     (nil? (:dir data))
     (om/build select-project data)

     (and (some? (:dir data)) (empty? (:srcs data)))
     (om/build explorer data)

     :else
     (om/build graph data))))

(defn init! []
  (om/root main app-state {:target  (.getElementById js/document "app")}))
