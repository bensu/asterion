(ns constable.core
  (:import [goog.ui IdGenerator])
  (:require [clojure.string :as str]
            [goog.string :as gstr]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.tools.namespace.file :as file]
            [constable.tree :as tree]
            [constable.deps :as deps]
            [constable.search :as search]
            [constable.project :as project]))

(enable-console-print!)

;; ====================================================================== 
;; Util

(def ipc (js/require "ipc"))

(def ipc-callbacks (atom {}))

(defn register! [k f]
  (when-not (contains? @ipc-callbacks k)
    (swap! ipc-callbacks assoc k f)
    (.on ipc k f)))

(def id-generator (IdGenerator.))

(defn new-id []
  (.getNextUniqueId id-generator))

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

(defn make-graph [platform srcs]
  (if (some? platform)
    (deps/depgraph platform srcs)
    (deps/depgraph srcs)))

;; TODO: move to deps
(defn file->ns-name [f]
  (-> f file/read-file-ns-decl rest first))

(defn call? [f]
  (try
    (f)
    (catch js/Object _ nil)))

(defn valid-graph? [graph]
  (not (empty? (:nodes graph))))

(defn filter-graph [graph ns]
  (letfn [(starts-with-any? [s]
            (->> ns
              (map #(re-find (js/RegExp. %) s))
              (some some?)))
          (node-matches? [node]
            (starts-with-any? (name (:name node))))
          (edge-matches? [edge]
            (->> (vals (select-keys edge [:target :source]))
              (map (comp starts-with-any? name))
              (some true?)))]
    (-> graph
      (update :nodes (comp vec (partial remove node-matches?)))
      (update :edges (comp vec (partial remove edge-matches?))))))

(defn highlight-graph [graph highlighted]
  {:pre [(map? graph) (set? highlighted)]}
  (let [highlighted (set (map name highlighted))]
    (update graph :nodes
      (partial mapv #(assoc % :highlight (contains? highlighted (:name %)))))))

(defn update-state [data [tag msg]]
  (case tag
    :project/start
    (let [srcs (:srcs msg)
          graph (make-graph (:platform (:project data)) srcs)]
      (-> data
        (assoc :graph graph :buffer graph)
        (assoc-in [:project :srcs] srcs)))

    ;; TODO: cleanup with just
    :project/add
    (let [{:keys [file platform]} msg 
          path (deps/file->folder file)] 
      (try
        (let [project-string (deps/read-file file)
              project-name (project/parse-name project-string)
              data' (update data :project
                      #(merge % {:root path
                                 :name project-name
                                 :platform platform}))]
          (try
            (let [srcs (->> (if (= :clj platform)
                              (project/parse-main-srcs project-string)
                              (project/parse project-string))
                         (map (partial deps/join-paths path))
                         set)]
              (update-state data' [:project/start {:srcs srcs}]))
            (catch js/Object e
              (assoc-in data' [:project :error] (.-message e)))))
        (catch js/Object _
          (update data :project
            #(merge % {:root path :platform platform})))))

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

    :nav/files-found (-> data 
                       (assoc-in [:nav :highlighted]
                         (->> (js->clj msg)
                           (remove empty?)
                           (map file->ns-name)
                           set))
                       (update-state [:nav/draw! nil]))
    
    :nav/draw! (let [graph (-> (:graph data)
                             (filter-graph (str/split (:ns (:nav data)) " "))
                             (highlight-graph (:highlighted (:nav data))))]
                 (if (valid-graph? graph)
                   (update-state data [:nav/graph->buffer graph])
                   (update-state data [:nav/add-error :graph/empty-nodes])))))

(defn raise! [data tag msg]
  {:pre [(keyword? tag) (om/cursor? data)]}
  (om/transact! data #(update-state % [tag msg])))

;; ====================================================================== 
;; Components


(defn button [platform f {:keys [value label] :as item}]
  (dom/div #js {:className "btn--green"
                :onClick (partial f item)}
    (or label value)))

(defn buttons [{:keys [platform items] :as data} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (register! "add-project-success"
        (fn [filename platform]
          (raise! data :project/add {:platform (keyword platform) 
                                     :file filename}))))
    om/IRender
    (render [_]
      (apply dom/div #js {:className "platform--btns"} 
        (butlast
          (interleave
            (map (partial button platform 
                   (fn [item _]
                     (.send ipc "request-project-dialog" (name (:value item)))))
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
        (dom/h1 #js {:className "blue-box__title"} "Constable")
        (dom/p nil "A tool to make and explore dependency graphs for Clojure(Script) projects.")
        (dom/p nil "To get started, open your pom.xml/project.clj for:")
        (om/build platform data)))))

(defn dir-item [item owner {:keys [click-fn]}]
  (om/component
    (dom/li nil
      (let [id (new-id)
            label (deps/file-name (:name item))]
        (dom/span nil
          (dom/input #js {:id id 
                          :className "folder-list__item"
                          :type "checkbox"
                          :checked (:selected? item)
                          :onClick click-fn})
          (dom/label #js {:htmlFor id} label))))))

(defn ls->srcs [ls]
  (->> ls 
    (filter :selected?)
    (map :name)
    set))

(defn clear-button [data owner]
  (om/component
    (dom/i #js {:className "fa fa-reply clear-btn float-right-corner"
                :title "Clear Project"
                :onClick (fn [_] (raise! data :project/clear nil))})))

(defn close-button [data owner {:keys [close-fn]}]
  (om/component
    (dom/i #js {:className "fa fa-times clear-btn float-right-corner"
                :title "Dismiss"
                :onClick (if (fn? close-fn)
                           close-fn
                           identity)})))

(defn src?
  "Tries to guess if the dir-name is a source directory"
  [dir-name]
  (some? (re-find #"src" dir-name)))

(defn error-card [{:keys [error] :as data} owner {:keys [close-fn class]}]
  (om/component
    (dom/div #js {:className class}
      (om/build close-button data {:opts {:close-fn close-fn}})
      (dom/h3 #js {:className "blue-box__subtitle"} (:title error))
      (dom/p nil (:msg error)))))

(defn explorer [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:error-on? true
       :ls (->> (deps/list-dirs (:root (:project data)))
             (mapv (fn [f] {:name f :selected? (src? f)})))})
    om/IRenderState
    (render-state [_ {:keys [ls error-on?]}]
      (dom/div #js {:className "center-container"}
        (when error-on?
          (om/build error-card
            (assoc data
              :error {:title "Blorgons!"
                      :msg (str "We couldn't read your pom.xml/project.clj"
                             #_(when-let [error (:error (:project data))]
                               (str ": " error)))})
            {:opts {:class "float-box center error-card"
                    :close-fn (fn [_]
                                (om/set-state! owner :error-on? false))}}))
        (dom/div #js {:className "float-box blue-box center"}
          (om/build clear-button data)
          (dom/h3 #js {:className "blue-box__subtitle"} "Source Paths")
          (dom/p nil "Would you mind selecting the source folders?")
          (if (empty? (:name (:project data)))
            (dom/strong nil (:root (:project data)))
            (dom/h3 #js {:className "blue-box__title"} (:name (:project data))))
          (apply dom/ul #js {:className "folder-list"} 
            (map-indexed 
              (fn [i dir]
                (om/build dir-item dir
                  {:opts {:click-fn (fn [_]
                                      (om/update-state! owner
                                        [:ls i :selected?] not))}}))
              ls))
          (dom/div #js {:className "btn-container--center"} 
            (dom/div
              #js {:className "btn--green btn-center"
                   :onClick (fn [_]
                              (raise! data :project/start
                                {:srcs (ls->srcs (om/get-state owner :ls))}))}
              "Explore")))))))

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
    om/IWillMount
    (will-mount [_]
      (register! "search-error"
        (fn [error]
          (raise! data :nav/search-error error)))
      (register! "search-success"
        (fn [fs]
          (raise! data :nav/files-found fs))))
    om/IRender
    (render [_]
      (dom/div #js {:className "float-box--side blue-box nav"} 
        (if (empty? (:name (:project data)))
          (dom/h3 #js {:className "blue-box__title"} "Constable")
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
                              (let [v (.. e -target -value)]
                                (if (empty? v)
                                  (do
                                    (raise! data :nav/clear-highlighted nil))
                                  (do
                                    (.send ipc "request-search"
                                      (clj->js (vec (:srcs (:project data))))
                                      (:highlight (:nav data)))))))
                  :value-key :highlight
                  :title "Enter a grep regex and I will highlight the ns that match"
                  :placeholder "highlight ns with grep"}})))))

(def error->msg {:graph/empty-nodes "We found nothing to graph"
                 :nav/search-error "There was a problem while searching"})

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
              :error {:title "Blorgons!"
                      :msg (error->msg (first (:errors data)))})
            {:opts {:class "notification error-card"
                    :close-fn (fn [_]
                                (raise! data :nav/clear-errors nil))}}))
        (om/build graph (:buffer data))))))

;; TODO: should be a multimethod
(defn main [data owner]
  (om/component
    (cond
      (empty? (:root (:project data)))
      (om/build select-project data)
      
      (and (not (empty? (:root (:project data))))
           (empty? (:srcs (:project data))))
      (om/build explorer data)

      :else
      (om/build graph-explorer data))))

(defn init! []
  (om/root main app-state {:target (.getElementById js/document "app")}))
