(ns constable.core
  (:import [goog.ui IdGenerator])
  (:require [clojure.string :as str]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.tools.namespace.file :as file]
            [constable.tree :as tree]
            [constable.deps :as deps]
            [constable.search :as search]
            [constable.project :as project]))

(enable-console-print!)

(def ipc (js/require "ipc"))

(def ipc-callbacks (atom {}))

(defn register! [k f]
  (when-not (contains? @ipc-callbacks k)
    (swap! ipc-callbacks assoc k f)
    (.on ipc k f)))

;; ====================================================================== 
;; Util

(def id-generator (IdGenerator.))

(defn new-id []
  (.getNextUniqueId id-generator))

;; ====================================================================== 
;; Model

(def init-state {:ns ""
                 :highlight ""
                 :highlighted #{}
                 :graph {}
                 :root "" 
                 :name ""
                 :srcs #{} 
                 :platform nil})

(defonce app-state (atom init-state))

;; ====================================================================== 
;; Update

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

    ;; TODO: cleanup with just
    :project/add
    (let [{:keys [file platform]} msg 
          path (deps/file->folder file)] 
      (try
        (let [project-string (deps/read-file file)
              project-name (project/parse-name project-string)
              srcs (->> (project/parse project-string)
                     (map (partial deps/join-paths path))
                     set)]
          (update-state [:project/start {:srcs srcs}] (assoc data
                                                        :root path
                                                        :name project-name
                                                        :platform platform)))
        (catch js/Object _
          (assoc data :root path :platform platform))))

    :project/clear init-state
    
    :project/platform (assoc data :platform msg)

    :nav/ns (assoc data :ns msg)
    
    :nav/highlight (assoc data :highlight msg)))

(defn raise! [data tag msg]
  {:pre [(keyword? tag) (om/cursor? data)]}
  (om/transact! data (partial update-state [tag msg])))

;; ====================================================================== 
;; Components

;; TODO: handle no graph - validation 
(defn draw! [data]
  (tree/drawTree "#graph"
    (clj->js
      {:ns (str/split (:ns data) " ")
       :highlighted (:highlighted data)})
    (clj->js (:graph data))))

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
        (dom/h2 #js {:className "blue-box__title"} "Constable")
        (dom/p nil "Open your pom.xml/project.clj for:")
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

(defn src?
  "Tries to guess if the dir-name is a source directory"
  [dir-name]
  (some? (re-find #"src" dir-name)))

(defn explorer [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:ls (->> (deps/list-dirs (:root data))
             (mapv (fn [f] {:name f :selected? (src? f)})))})
    om/IRenderState
    (render-state [_ {:keys [ls]}]
      (dom/div #js {:className "center-container"}
        (dom/div #js {:className "float-box blue-box center"}
          (om/build clear-button data)
          (dom/h3 #js {:className "blue-box__title"} "Yikes!")
          (dom/p nil "We couldn't read your pom.xml/project.clj")
          (dom/p nil "Would you mind selecting the source folders?")
          (if (empty? (:name data))
            (dom/strong nil (:root data))
            (dom/h3 #js {:className "blue-box__title"} (:name data)))
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

(defn nav-input [data owner {:keys [value-key placeholder
                                    on-change on-enter]}]
  (om/component
    (dom/input #js {:className "blue-input"
                    :type "text"
                    :placeholder placeholder 
                    :value (get data value-key)
                    :onKeyDown (fn [e]
                                 (when (= "Enter" (.-key e))
                                   (on-enter e)))
                    :onChange (fn [e]
                                (on-change (.. e -target -value)))})))

(defn file->ns-name [f]
  (-> f file/read-file-ns-decl rest first))

(defn nav [data owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (register! "search-success"
        (fn [fs]
          (let [new-data (assoc @data :highlighted
                           (->> (js->clj fs)
                             (remove empty?)
                             (map file->ns-name)
                             set))]
            (om/update! data new-data)
            (draw! new-data)))))
    om/IRender
    (render [_]
      (dom/div #js {:className "float-box--side blue-box nav"} 
        (dom/h3 #js {:className "project-name"}
          (if-not (empty? (:name data))
            (:name data)
            "Constable"))
        (om/build clear-button data)
        (om/build nav-input data
          {:opts {:on-change (partial raise! data :nav/ns)
                  :on-enter (fn [_] (draw! data))
                  :value-key :ns
                  :placeholder "filter ns"}})
        (om/build nav-input data
          {:opts {:on-change (partial raise! data :nav/highlight)
                  :on-enter (fn [e]
                              (let [v (.. e -target -value)]
                                (if (empty? v)
                                  (do
                                    (om/update! data :highlighted #{})
                                    (draw! (assoc data :highlighted #{})))
                                  (do
                                    (.send ipc "request-search"
                                      (clj->js (vec (:srcs data)))
                                      (:highlight data))))))
                  :value-key :highlight
                  :placeholder "highlight ns"}})))))

;; TODO: should show a loader
(defn graph [data owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "page"} 
        (om/build nav data)
        (dom/svg #js {:id "graph"}
          (dom/g #js {}))))
    om/IDidMount
    (did-mount [_]
      (when-not (empty? (:graph @data))
        (draw! @data)))))

;; TODO: should be a multimethod
(defn main [data owner]
  (om/component
    (cond
      (empty? (:root data))
      (om/build select-project data)
      
      (and (not (empty? (:root data))) (empty? (:srcs data)))
      (om/build explorer data)

      :else
      (om/build graph data))))

(defn init! []
  (om/root main app-state {:target (.getElementById js/document "app")}))
