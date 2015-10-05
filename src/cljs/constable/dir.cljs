(ns constable.dir
  (:require [cljs.node.io :as io]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [constable.components :as components]))

(defn src?
  "Tries to guess if the dir-name is a source directory"
  [dir-name]
  (some? (re-find #"src" dir-name)))

(defn ls->srcs [ls]
  (->> ls 
    (filter :selected?)
    (map :name)
    set))

(defn root->list-dir [root]
  (->> (io/list-dirs root)
       (mapv (fn [f] {:name f}))))

(declare list-dir)

(defn dir-item [{:keys [srcs dir]} owner {:keys [click-fn] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:expand? false
       :ls (root->list-dir (:name dir))})
    om/IRenderState
    (render-state [_ {:keys [ls expand?]}]
      (let [selected? (contains? srcs (:name dir))]
        (dom/li #js {:className "file-item"} 
          (dom/span #js {:className (str "clickable "
                                      (if selected?
                                        "file-item__text--activated"
                                        "file-item__text"))
                         :onClick (partial click-fn (:name dir))
                         :title (if selected?
                                  "Unselect directory"
                                  "Select directory")}
            (io/file-name (:name dir)))
          (when-not (empty? ls)
            (om/build components/icon-button
              {:title "Expand directory"
               :icon-class (str "fa-chevron-down " 
                             (if expand?
                               "expand-icon--active"
                               "expand-icon"))}
              {:opts {:click-fn (fn [_]
                                  (om/update-state! owner :expand? not))}}))
          (dom/div #js {:className "divider"} nil)
          (when expand?
            (om/build list-dir {:srcs srcs :ls ls} {:opts opts})))))))

(defn list-dir [{:keys [srcs ls]} owner opts]
  (om/component
    (apply dom/ul #js {:className "folder-list"} 
      (map-indexed 
        (fn [i dir]
          (om/build dir-item {:dir dir :srcs srcs} {:opts opts}))
        ls))))
