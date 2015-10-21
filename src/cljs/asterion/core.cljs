(ns asterion.core
  (:require [clojure.string :as str]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [asterion.d3]
            [asterion.tree :as tree]
            [asterion.project :as project]
            [asterion.components :as components]))

(enable-console-print!)

;; ====================================================================== 
;; Model

(def init-state {:nav {:ns ""
                       :highlight "" ;; can be local state
                       :highlighted #{}}
                 :buffer {}
                 :graph {}
                 :errors #{}
                 :project {:root ""
                           :name ""
                           :srcs #{} 
                           :platform nil}})

(defonce app-state (atom init-state))

;; ====================================================================== 
;; Update

(def error->msg* 
  {:graph/empty-nodes "We found nothing to graph"
   :project/parse-error "We couldn't read your project.clj"
   :nav/search-error "There was a problem while searching"
   :nav/search-not-found "No matches found"})

(defn error->msg [error]
  (if (string? error)
    error
    (error->msg* error)))

(defn update-state [data [tag msg]]
  (case tag
    ;; :project/start
    ;; (try
    ;;   (let [srcs (:srcs msg)
    ;;         graph (deps/depgraph (:platform (:project data)) srcs)]
    ;;     (-> data
    ;;       (assoc :graph graph :buffer graph :errors #{})
    ;;       (assoc-in [:project :srcs] srcs)))
    ;;   (catch :default e
    ;;     (assoc data :errors #{(.-message e)})))

    ;; TODO: cleanup with just
    ;; :project/add
    ;; (let [{:keys [file platform]} msg 
    ;;       path (io/file->folder file)
    ;;       ext (io/extension file)] 
    ;;   (try
    ;;     (let [project-string (io/read-file file)
    ;;           project-name (if (= ".xml" ext)
    ;;                          (project/parse-pom-name project-string)
    ;;                          (project/parse-project-name project-string))
    ;;           data' (update data :project
    ;;                   #(merge % {:root path
    ;;                              :name project-name
    ;;                              :platform platform}))]
    ;;       (try
    ;;         (let [srcs (->> (if (= :clj platform)
    ;;                           (project/parse-main-srcs project-string)
    ;;                           (project/parse project-string))
    ;;                      (map (partial io/join-paths path))
    ;;                      set)]
    ;;           (update-state data' [:project/start {:srcs srcs}]))
    ;;         (catch :default e
    ;;           (assoc data' :errors #{:project/parse-error}))))
    ;;     (catch :default _
    ;;       (update data :project
    ;;         #(merge % {:root path :platform platform})))))

    :project/clear init-state
    
    ;; No longer used
    :project/platform (assoc-in data [:project :platform] msg)

    :nav/graph->buffer (assoc data :buffer msg)

    :nav/ns (assoc-in data [:nav :ns] msg)
    
    :nav/highlight (assoc-in data [:nav :highlight] msg)
    
    :nav/clear-highlighted (-> data
                             (assoc-in [:nav :highlighted] #{})
                             (update-state [:nav/draw! nil]))
    
    :nav/add-error (update data :errors #(conj % msg))
    
    :nav/clear-errors (assoc data :errors #{})
    
    :nav/search-error (assoc data :errors #{:nav/search-error})

    :nav/search-not-found (assoc data :errors #{:nav/search-not-found})

    ;; :nav/files-found (-> data 
    ;;                    (assoc-in [:nav :highlighted]
    ;;                      (->> (js->clj msg)
    ;;                        (remove empty?)
    ;;                        (map deps/file->ns-name)
    ;;                        set))
    ;;                    (update-state [:nav/draw! nil]))
-    
    ;; :nav/draw! (let [graph (-> (:graph data)
    ;;                          (deps/filter-graph (str/split (:ns (:nav data)) " "))
    ;;                          (deps/highlight-graph (:highlighted (:nav data))))]
    ;;              (if (deps/valid-graph? graph)
    ;;                (update-state data [:nav/graph->buffer graph])
    ;;                (update-state data [:nav/add-error :graph/empty-nodes])))
))

(defn raise! [data tag msg]
  {:pre [(keyword? tag) (om/cursor? data)]}
  (om/transact! data #(update-state % [tag msg])))

;; ====================================================================== 
;; Project Screen 

(defn button [platform f {:keys [value label] :as item}]
  (dom/div #js {:className "btn--green"
                :onClick (partial f item)}
    (or label value)))

(defn buttons [{:keys [platform items] :as data} owner]
  (reify
    om/IRender
    (render [_]
      (apply dom/div #js {:className "platform--btns"} 
        (butlast
          (interleave
            (map (partial button platform identity)
              items)
            (map (fn [_] (dom/br nil nil)) (range (count items)))))))))

(defn platform [data owner]
  (om/component
    (dom/div #js {:className "platform--toggle"} 
      (om/build buttons 
        (assoc data 
          :items (mapv (fn [[v l]] {:value v :label l}) 
                   [[:clj "clj"] [:cljs "cljs"] [:cljc "both"]]))))))

(defn select-project [data owner]
  (om/component
    (dom/div #js {:className "center-container"}
      (dom/div #js {:className "float-box blue-box center"}
        (dom/h1 #js {:className "blue-box__title"} "Asterion")
        (dom/p nil "Make and explore dependency graphs for Clojure(Script) projects.")
        (dom/p nil "To get started, open your project.clj for:")
        (om/build platform data)))))

;; ====================================================================== 
;; Sources Screen

(defn clear-button [data owner]
  (om/component
    (om/build components/icon-button
      {:title "Clear Project"
       :icon-class "fa-reply float-right-corner clear-btn"}
      {:opts {:click-fn (fn [_]
                          (raise! data :project/clear nil))}})))

(defn error-card [{:keys [error] :as data} owner {:keys [close-fn class]}]
  (om/component
    (dom/div #js {:className class}
      (om/build components/icon-button
        {:icon-class "fa-times float-right-corner clear-btn"
         :title "Dismiss"}
        {:opts {:click-fn close-fn}})
      (dom/h3 #js {:className "blue-box__subtitle"
                   :title "A veces me equivoco y nos reimos buenamente los dos"}
        (:title error))
      (dom/p nil (:msg error)))))

;; ====================================================================== 
;; Graph Screen

(defn nav-input [data owner {:keys [value-key placeholder title
                                    on-change on-enter]}]
  (om/component
    (dom/input #js {:className "blue-input"
                    :type "text"
                    :title title
                    :placeholder placeholder 
                    :value (get data value-key)
                    :onKeyDown (fn [e]
                                 (when (= "Enter" (.-key e))
                                   (on-enter e)))
                    :onChange (fn [e]
                                (on-change (.. e -target -value)))})))

(defn nav [data owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "float-box--side blue-box nav"} 
        (if (empty? (:name (:project data)))
          (dom/h3 #js {:className "blue-box__title"} "Asterion")
          (dom/h3 #js {:className "project-name"} (:name (:project data))))
        (om/build clear-button data)
        (om/build nav-input (:nav data)
          {:opts {:on-change (partial raise! data :nav/ns)
                  :on-enter (fn [_] (raise! data :nav/draw! nil))
                  :title "I'll remove the ns with names that match these words"
                  :value-key :ns
                  :placeholder "filter ns"}})
        (om/build nav-input (:nav data)
          {:opts {:on-change (partial raise! data :nav/highlight)
                  :on-enter (fn [e]
                              (if (empty? (.. e -target -value))
                                (raise! data :nav/clear-highlighted nil)))
                  :value-key :highlight
                  :title "Enter a grep regex and I will highlight the ns that match"
                  :placeholder "highlight ns with grep"}})))))



(defn graph [buffer owner]
  (letfn [(draw! []
            (when-not (empty? @buffer)
              (tree/drawTree "#graph" (clj->js @buffer))))]
    (reify
      om/IRender
      (render [_]
        (dom/svg #js {:id "graph"}
          (dom/g #js {})))
      om/IDidUpdate
      (did-update [_ _ _]
        (draw!))
      om/IDidMount
      (did-mount [_]
        (draw!)))))

;; TODO: should show a loader
(defn graph-explorer [data owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "page"} 
        (om/build nav data)
        (when-not (empty? (:errors data))
          (om/build error-card
            (assoc data
              :error {:title "Error" 
                      :msg (error->msg (first (:errors data)))})
            {:opts {:class "notification error-card"
                    :close-fn (fn [_]
                                (raise! data :nav/clear-errors nil))}}))
        (om/build graph (:buffer data))))))

;; ====================================================================== 
;; Component Dispatcher

;; TODO: should be a multimethod
(defn main [data owner]
  (om/component
    (cond
      (empty? (:root (:project data)))
      (om/build select-project data)
      
      ;; (and (not (empty? (:root (:project data))))
      ;;      (empty? (:srcs (:project data))))
      ;; (om/build srcs-component data)

      :else
      (om/build graph-explorer data))))

(defn init! []
  (om/root main app-state {:target (.getElementById js/document "app")}))
