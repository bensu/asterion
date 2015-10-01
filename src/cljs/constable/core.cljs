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

;; TODO: handle no graph 
(defn draw! [data]
  (tree/drawTree "#graph"
    (clj->js
      {:ns (str/split (:ns data) " ")
       :highlight (str/split (:highlight data) " ")})
    (clj->js (:graph data))))

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

(defn update-state [[tag msg] data]
  (case tag
    :project/start
    (let [srcs (:srcs msg)
          graph (make-graph (:platform data) srcs)]
      (assoc data :graph graph :srcs srcs))

    :project/add
    (let [f (:f msg)
          path (deps/file->folder f)] 
      (try
        (let [srcs (->> (project/parse (deps/read-file f))
                     (map (partial deps/join-paths path))
                     set)]
          (update-state [:project/start {:srcs srcs}] (assoc data :root path)))
        (catch js/Object _
          (assoc data :root path))))

    :project/clear (dissoc data :root :srcs :ls)

    :nav/ns (assoc data :ns msg)
    
    :nav/highlight (assoc data :highlight msg)))

(defn raise! [data tag msg]
  {:pre [(keyword? tag) (om/cursor? data)]}
  (om/transact! data (partial update-state [tag msg])))

(defn select-project [data owner]
  (om/component
    (dom/div nil
      (dom/h1 nil "Select your project folder!")
      (om/build platform data)
      (dom/input #js {:type "file"
                      :onChange
                      (fn [e]
                        (.preventDefault e)
                        (raise! data :project/add {:f (.-path (e->file e))}))}))))

(defn dir-item [item owner {:keys [click-fn]}]
  (om/component
    (dom/li nil
      (dom/span nil
        (dom/input #js {:type "checkbox"
                        :checked (:selected? item)
                        :onClick click-fn})
        (deps/file-name (:name item))))))

(defn ls->srcs [ls]
  (->> ls 
    (filter :selected?)
    (map :name)
    set))

(defn explorer [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:ls (->> (deps/list-dirs (:root data))
             (mapv (fn [f] {:name f :selected? false})))})
    om/IRenderState
    (render-state [_ {:keys [ls]}]
      (dom/div nil
        (dom/p nil "We couldn't parse your project.clj Would you choosing the folders?")
        (dom/p nil (:root data))
        (apply dom/ul nil
          (map-indexed 
            (fn [i dir]
              (om/build dir-item dir
                {:opts {:click-fn (fn [_]
                                    (om/update-state! owner
                                      [:ls i :selected?] not))}}))
            ls))
        (dom/button
          #js {:onClick (fn [_]
                          (raise! data :project/start
                            {:srcs (ls->srcs (om/get-state owner :ls))}))}
          "Explore!")))))

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
                                        (raise! data :nav/ns
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
                                        (raise! data :nav/highlight
                                          (.. e -target -value)))})))
        (dom/svg #js {:id "graph"}
          (dom/g #js {}))))
    om/IDidMount
    (did-mount [_]
      (when (and (not (empty? (:graph @data))))
        (draw! @data)))))



;; TODO: should be a multimethod
(defn main [data owner]
  (om/component
    (dom/div nil
      (dom/h1 nil "Constable")
      (dom/button #js {:onClick (fn [_] (raise! data :project/clear nil))} "X")
      (cond
        (empty? (:root data))
        (om/build select-project data)
        
        (and (not (empty? (:root data))) (empty? (:srcs data)))
        (om/build explorer data)

        :else
        (om/build graph data)))))

(defn init! []
  (om/root main app-state {:target  (.getElementById js/document "app")}))
