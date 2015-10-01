(ns constable.core
  (:require [clojure.string :as str]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [constable.tree :as tree]
            [constable.deps :as deps]
            [constable.project :as project]))

(enable-console-print!)

(defonce app-state (atom {:ns ""
                          :highlight ""
                          :root "" 
                          :srcs #{} 
                          :graph {}
                          :platform :clj}))

(defn draw! [data]
  (tree/drawTree "#graph"
    (clj->js
      {:ns (str/split (:ns data) " ")
       :highlight (str/split (:highlight data) " ")})
    (clj->js (:graph data))))

(defn dir-item [item owner]
  (om/component
    (dom/li nil
      (dom/span nil
        (dom/input #js {:type "checkbox"
                        :checked (:selected? item)
                        :onClick (fn [e]
                                   (om/transact! item :selected? not))})
        (deps/file-name (:name item))))))

(defn radio [name f {:keys [value label]}]
  (dom/span nil
    (dom/input #js {:id value :type "radio" :name name
                    :value value :onChange (f value)})
    (dom/label #js {:htmlFor value} (or label value))))

(defn radios [{:keys [platform items] :as data} owner]
  (om/component
    (apply dom/ul nil
      (interleave
        (map (partial radio "platform"
               (fn [item-value]
                 (fn [_]
                   (om/update! data :platform (keyword item-value)))))
          items)
        (map (fn [_] (dom/br nil nil)) (range (count items)))))))

(defn e->file [e]
  (aget (or (.. e -target -files)
            (.. e -target -value)
            (.. e -dataTransfer -files))
    0))

(defn platform [data owner]
  (om/component
    (dom/div nil
      (om/build radios (assoc data 
                         :items (mapv (partial hash-map :value)
                                      ["clj" "cljs" "cljc"]))))))

(defn make-graph [platform srcs]
  (if (some? platform)
    (deps/depgraph platform srcs)
    (deps/depgraph srcs)))

(defn select-project [data owner]
  (om/component
    (dom/div nil
      (dom/h1 nil "Select your project folder!")
      (om/build platform data)
      (dom/input #js {:type "file"
                      :onChange
                      (fn [e]
                        (.preventDefault e)
                        (let [f (.-path (e->file e)) 
                              path (deps/file->folder f)]
                          (try
                            (let [srcs (->> (project/parse (deps/read-file f))
                                         (map (partial deps/join-paths path))
                                         set)
                                  graph (try
                                          (make-graph (:platform data) srcs)
                                          (catch js/Object e
                                            (.log js/console e)))]
                              (om/transact! data 
                                #(assoc % :root path :srcs srcs :graph graph)))
                            (catch js/Object _
                              (om/transact! data
                                #(assoc %
                                   :root path
                                   :ls (->> (deps/list-dirs path)
                                         (mapv (fn [f]
                                                 {:name f
                                                  :selected? false})))))))))}))))

(defn explorer [data owner]
  (om/component
    (dom/div nil
      (dom/p nil "We couldn't parse your project.clj Would you choosing the folders?")
      (dom/p nil (:root data))
      (apply dom/ul nil
        (om/build-all dir-item (:ls data)))
      (dom/button
        #js {:onClick (fn [_]
                        (let [srcs (or (:srcs data)
                                       (->> (:ls data)
                                            (filter :selected?)
                                            (map :name)
                                            set))
                              graph (make-graph (:platform data) srcs)]
                          (om/transact! data
                            #(assoc % :graph graph :srcs srcs))))}
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
    om/IDidMount
    (did-mount [_]
      (when (and (not (empty? (:graph @data))))
        (draw! @data)))))

(defn clear! [data]
  (om/transact! data #(dissoc % :root :srcs :ls)))

;; TODO: should be a multimethod
(defn main [data owner]
  (om/component
    (dom/div nil
      (dom/h1 nil "Constable")
      (dom/button #js {:onClick (fn [_]
                                  (clear! data))}
        "X")
      (cond
        (empty? (:root data))
        (om/build select-project data)
        
        (and (not (empty? (:root data))) (empty? (:srcs data)))
        (om/build explorer data)

        :else
        (om/build graph data)))))

(defn init! []
  (om/root main app-state {:target  (.getElementById js/document "app")}))
