(ns asterion.core
  (:require [clojure.string :as str]
            [cljs.reader :as reader]
            [ajax.core :refer [GET POST]]
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
                 :waiting? true
                 :buffer {}
                 :graph {}
                 :errors #{}
                 :project {:url ""
                           :user nil ;; string 
                           :repo nil ;; string 
                           :srcs #{} 
                           :platform nil}})

(defonce app-state (atom init-state))

;; ====================================================================== 
;; Update

(def error->msg* 
  {:graph/empty-nodes "We found nothing to graph after filtering"
   :project/parse-error "We couldn't read your project.clj"
   :project/not-found "We couldn't find the repository"
   :project/protected "The repository is either protected or not there!"
   :project/timeout "It took too long to talk to the server. We don't know what happened!"
   :project/no-project-file "We couldn't find a project.clj in the repository top level (nested project.clj are not supported yet)"
   :nav/search-error "There was a problem while searching"
   :nav/search-not-found "No matches found"
   :unknown-error "We are truly sorry but we don't know what happened."})

(defn error->msg [error]
  (if (string? error)
    error
    (error->msg* error)))

(defn update-state [data [tag msg]]
  (case tag
    :graph/add (-> data
                 (assoc :graph msg)
                 (update-state [:nav/graph->buffer msg])
                 (update-state [:nav/draw! nil]))

    :project/data (update data :project #(merge % msg))

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

(def examples
  ["https://github.com/clojure/clojurescript"
   "https://github.com/ztellman/manifold"
   "https://github.com/aphyr/riemann"
   "https://github.com/metabase/metabase"
   "https://github.com/Engelberg/instaparse"
   "https://github.com/overtone/overtone"
   "https://github.com/juxt/yada"])

(defn error-handler [data err]
  (raise! data :project/done nil)
  (raise! data :nav/add-error
    (try
      (if (= :timeout (:failure err))
        :project/timeout
        (let [res (reader/read-string (:response err))]
          (if (keyword? (:error res))
            (:error res)
            :unknown-error)))
      (catch :default e
        :unknown-error))))

(defn start! [data e]
  (raise! data :nav/clear-errors nil)
  (let [url (:url (:project data))
        [user repo] (take-last 2 (str/split url "/"))]
    ;; validation?
    (raise! data :project/wait nil)
    (raise! data :project/data {:user user :repo repo})
    (GET (str "/repo/" user "/" repo)
      {:handler (fn [res]
                  (raise! data :project/done nil)
                  (raise! data :graph/add
                    (:graph (reader/read-string res))))
       :error-handler (partial error-handler data)})))

(defn button [label f]
  (dom/div #js {:className "btn--green"
                :onClick f}
    label))

(defn project-name [data]
  (let [project (:project data)]
    (when-let [repo (:repo project)]
      (when-let [user (:user project)]
        (str user "/" repo)))))

(defn example-link [link owner]
  (om/component
    (dom/li #js {:className "file-item"} 
      (dom/a #js {:className "file-item__text"
                  :target "_blank"
                  :href link}
        link))))

(defn select-project [data owner]
  (om/component
    (dom/div #js {:className "page center-container whole-page"}
      (when-not (empty? (:errors data))
        (om/build error-card
          (assoc data
            :error {:title "Error" 
                    :msg (error->msg (first (:errors data)))})
          {:opts {:class "float-box error-card center"
                  :close-fn (fn [_]
                              (raise! data :nav/clear-errors nil))}}))
      (dom/div #js {:className "float-box blue-box center"}
        (dom/h1 #js {:className "blue-box__title"} "Asterion")
        (if true ;; (:waiting? data)
          (dom/div nil
            (dom/p nil (str "Processing " (or (project-name data) "...")))
            (dom/p nil "Try other cool projects:")
            (apply dom/ul #js {:className "folder-list"} 
              (->> (shuffle examples)
                (take 3)
                (map (partial om/build example-link))))
            (dom/p nil
              (dom/span nil "but whatever you do, don't try "
                (dom/a #js {:className "file-item__text"
                            :href "https://github.com/clojure/core.typed"
                            :target "_blank"}
                  "core.typed"))))
          (dom/div nil
            (dom/p nil "Make dependency graphs for Clojure projects.")
            (dom/p nil "Paste a link for a github repo")
            (dom/input #js {:type "url"
                            :className "blue-input"
                            :placeholder (str "Ex: " (rand-nth examples))
                            :value (:url (:project data))
                            :onKeyDown (fn [e]
                                         (when (= "Enter" (.-key e))
                                           (start! data e)))
                            :onChange (fn [e]
                                        (let [url (.. e -target -value)]
                                          (raise! data :project/url url)))})
            (dom/div #js {:className "center-container"}
              (button "Graph" (partial start! data)))))))))

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
        (if (empty? (:repo (:project data)))
          (dom/h3 #js {:className "blue-box__title"} "Asterion")
          (dom/h3 #js {:className "project-name"} (:repo (:project data))))
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
            {:opts {:class "notification float-box error-card"
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
