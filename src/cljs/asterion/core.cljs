(ns asterion.core
  (:require [clojure.string :as str]
            [cljs.reader :as reader]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [asterion.d3]
            [asterion.deps :as deps]
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
                 :project {:url ""
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

    :graph/add (-> data
                 (assoc :graph msg)
                 (update-state [:nav/graph->buffer msg]))

    :project/url (assoc-in data [:project :url] msg)
    
    :project/wait (assoc data :waiting? true)
    
    :project/done (assoc data :waiting? false)

    :project/clear init-state
    
    :nav/graph->buffer (assoc data :buffer msg)

    :nav/ns (assoc-in data [:nav :ns] msg)
    
    :nav/highlight (assoc-in data [:nav :highlight] msg)
    
    :nav/clear-highlighted (-> data
                             (assoc-in [:nav :highlighted] #{})
                             (update-state [:nav/draw! nil]))
    
    :nav/add-error (update data :errors #(conj % msg))
    
    :nav/clear-errors (assoc data :errors #{})
    
    :nav/draw! (let [g (-> (:graph data)
                         (deps/filter-graph (str/split (:ns (:nav data)) " "))
                         (deps/highlight-graph (:highlighted (:nav data))))]
                 (if (deps/valid-graph? g)
                   (update-state data [:nav/graph->buffer g])
                   (update-state data [:nav/add-error :graph/empty-nodes])))
    
    ;; If action is unknonw, return data unchanged 
    data))

(defn raise! [data tag msg]
  {:pre [(keyword? tag) (om/cursor? data)]}
  (om/transact! data #(update-state % [tag msg])))

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
;; Project Screen 

(defn button [platform f {:keys [value label] :as item}]
  (dom/div #js {:className "btn--green"
                :onClick (partial f item)}
    (or label value)))

(def serial-project
  (reader/read-string
    "{:edges [{:source asterion.dir, :target asterion.components} {:source asterion.dir, :target cljs.node.io} {:source asterion.core, :target asterion.d3} {:source asterion.core, :target asterion.components} {:source asterion.core, :target asterion.project}], :nodes [{:name \"asterion.dir\"} {:name \"cljs.node.io\"} {:name \"asterion.core\"} {:name \"asterion.d3\"} {:name \"asterion.components\"} {:name \"asterion.project\"} {:name \"asterion.deps\"}]}"))

(defn select-project [data owner]
  (om/component
    (dom/div #js {:className "page center-container"}
      (when-not (empty? (:errors data))
        (om/build error-card
          (assoc data
            :error {:title "Error" 
                    :msg (error->msg (first (:errors data)))})
          {:opts {:class "notification error-card"
                  :close-fn (fn [_]
                              (raise! data :nav/clear-errors nil))}}))
      (dom/div #js {:className "float-box blue-box center"}
        (dom/h1 #js {:className "blue-box__title"} "Asterion")
        (dom/p nil "Make and explore dependency graphs for Clojure projects.")
        (dom/p nil "To get started, open your project.clj for:")
        (dom/input #js {:type "url"
                        :className "blue-input"
                        :value (:url (:project data))
                        :onChange (fn [e]
                                    (let [url (.. e -target -value)]
                                      (raise! data :project/url url)))})
        (if (:waiting? data)
          (dom/p nil "Processing project")
          (dom/button
            #js {:onClick (fn [_]
                            (raise! data :project/wait nil)
                            (js/setTimeout
                              (fn []
                                (raise! data :project/done nil)
                                (if true 
                                  (raise! data :graph/add serial-project)
                                  (raise! data :nav/add-error "We couldn't load the project")))
                              1000))}
            "Go"))))))

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
                  :placeholder "filter ns"}})))))

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
    (if (empty? (:graph data))
      (om/build select-project data)
      (om/build graph-explorer data))))

(defn init! []
  (om/root main app-state {:target (.getElementById js/document "app")}))
