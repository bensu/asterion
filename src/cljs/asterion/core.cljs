(ns asterion.core
  (:import [goog.dom ViewportSizeMonitor])
  (:require [goog.string.linkify :as links]
            [goog.events :as events]
            [clojure.string :as str]
            [cljs.reader :as reader]
            [ajax.core :refer [GET POST]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [asterion.d3]
            [asterion.deps :as deps]
            [asterion.tree :as tree]
            [asterion.project :as project]
            [asterion.components :as components]
            [asterion.analytics :refer [repo-event! nav-event!]]
            [asterion.click :as click]))

;; ======================================================================
;; Model

(def init-state {:nav {:ns ""
                       :highlight "" ;; can be local state
                       :open? false
                       :build nil
                       :highlighted #{}}
                 :buffer {}
                 :graphs []
                 :errors #{}
                 :project {:url ""
                           :user nil ;; string
                           :repo nil ;; string
                           :srcs #{}
                           :platform nil}})

(defonce app-state (atom init-state))

;; ======================================================================
;; Update

(def ->form
  [(dom/p nil "We couldn't find the repository. ")
   (dom/p nil "Do you want to graph a "
     (components/forms-link "private repository?"))])

(def error->msg*
  {:graph/empty-nodes "We found nothing to graph after filtering"
   :project/parse-error "We couldn't read your project.clj"
   :project/not-found "We couldn't find the repository"
   :project/protected ->form
   :project/timeout "It took too long to talk to the server. We don't know what happened!"
   :project/no-project-file "We couldn't find a project.clj in the repository's top level directory."
   :project/invalid-url "The given url is invalid. Try copy-pasting from the url bar, ex: https://github.com/juxt/yada"
   :project/circular-dependency "The project contains a circular dependency, probably a cljs file requiring a clj macro file with the same name. This is a bug, we'll fix it soon!"
   :nav/search-error "There was a problem while searching"
   :nav/search-not-found "No matches found"
   :unknown-error "We are truly sorry but we don't know what happened."})

(defn error->msg [error]
  (if (string? error)
    error
    (error->msg* error)))

(defn update-state [data [tag msg]]
  (case tag
    :graph/add (let [build-id (or (first (keys msg)) "clj")]
                 (-> data
                   (assoc :graphs msg)
                   (assoc-in [:nav :build] build-id)
                   (update-state [:nav/graph->buffer (get msg build-id)])
                   (update-state [:nav/draw! nil])))

    :project/data (update data :project #(merge % msg))

    :project/url (assoc-in data [:project :url] msg)

    :project/wait (assoc data :waiting? true)

    :project/done (assoc data :waiting? false)

    :project/invalid-url (update data :errors #(conj % :project/invalid-url))

    :project/clear init-state

    :nav/new-build (-> data
                     (assoc-in [:nav :build] msg)
                     (update-state [:nav/graph->buffer (get (:graphs data) msg)])
                     (update-state [:nav/draw! nil]))

    :nav/toggle-builds (update-in data [:nav :open?] not)

    :nav/open-help (do (nav-event! "open-help" nil)
                       (assoc data :overlay? true))

    :nav/close-help (do (nav-event! "close-help" nil)
                        (assoc data :overlay? false))

    :nav/graph->buffer (assoc data :buffer msg)

    :nav/ns (assoc-in data [:nav :ns] msg)

    :nav/highlight (assoc-in data [:nav :highlight] msg)

    :nav/clear-highlighted (-> data
                             (assoc-in [:nav :highlighted] #{})
                             (update-state [:nav/draw! nil]))

    :nav/add-error (update data :errors #(conj % msg))

    :nav/clear-errors (assoc data :errors #{})

    :nav/draw! (let [g (-> (get (:graphs data) (:build (:nav data)))
                         (deps/filter-graph (str/split (:ns (:nav data)) " "))
                         (deps/highlight-graph (:highlighted (:nav data))))]
                 (if (deps/valid-graph? g)
                   (update-state data [:nav/graph->buffer g])
                   (update-state data [:nav/add-error :graph/empty-nodes])))

    ;; If action is unknonw, return data unchanged
    data))

;; TODO: support raising several events sequentially
(defn raise! [data tag msg]
  {:pre [(keyword? tag) (om/cursor? data)]}
  (om/transact! data #(update-state % [tag msg])))

;; ======================================================================
;; Sources Screen

(defn help-button [data owner]
  (om/component
    (om/build components/icon-button
      {:title "Help & About"
       :icon-class "fa-question float-bottom-corner help-btn"}
      {:opts {:click-fn (fn [_]
                          (raise! data :nav/open-help nil))}})))

(defn close-button [data owner]
  (om/component
    (om/build components/icon-button
      {:title "Close Help"
       :icon-class "fa-times float-right-corner clear-btn"}
      {:opts {:click-fn (fn [_]
                          (raise! data :nav/close-help nil))}})))

(defn clear-button [data owner]
  (om/component
    (om/build components/icon-button
      {:title "Clear Project"
       :icon-class "fa-reply float-right-corner clear-btn"}
      {:opts {:click-fn (fn [_]
                          (nav-event! "clear-repo" (:url (:project data)))
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
      (cond
        (string? (:msg error)) (dom/p nil (:msg error))
        :else (apply dom/div nil (:msg error))))))


;; ======================================================================
;; Project Screen

(def examples-with-path
  [["https://github.com/bhauman/lein-figwheel" "sidecar"]
   ["https://github.com/aphyr/jepsen" "jepsen"]
   ["https://github.com/emezeske/lein-cljsbuild" "support"]])

(def examples
  ["https://github.com/clojure/clojurescript"
   "https://github.com/circleci/frontend"
   "https://github.com/tonsky/datascript"
   "https://github.com/ztellman/manifold"
   "https://github.com/aphyr/riemann"
   "https://github.com/onyx-platform/onyx"
   "https://github.com/metabase/metabase"
   "https://github.com/Engelberg/instaparse"
   "https://github.com/overtone/overtone"
   "https://github.com/juxt/yada"])

(defn error-handler [data err]
  (raise! data :project/done nil)
  (let [e (try
            (if (= :timeout (:failure err))
              :project/timeout
              (let [res (reader/read-string (:response err))]
                (if (keyword? (:error res))
                  (:error res)
                  :unknown-error)))
            (catch :default e
              :unknown-error))]
    (repo-event! "receive-error" (clj->js e))
    (raise! data :nav/add-error e)))

(defn handler [data res]
  (repo-event! "receive-cached" (:url (:project data)))
  (raise! data :project/done nil)
  (raise! data :graph/add (:graphs (reader/read-string res))))

;; TODO: this function does the unexpected when the repo *does*
;; contain ".git" in the name!
(defn remove-git-ext [^string url]
  (str/replace url #"\.git$" ""))

(defn parse-url [url]
  (let [url' (links/findFirstUrl url)]
    (when-not (empty? url')
      (let [tokens (take-last 2 (str/split (remove-git-ext url') "/"))]
        (when (and (every? (comp not empty?) tokens)
                   (not (re-find #"github" (first tokens))))
          tokens)))))

(defn start! [data e]
  (raise! data :nav/clear-errors nil)
  (let [url (:url (:project data))]
    (repo-event! "start" url)
    (if-let [[user repo] (parse-url url)]
      (do
        (raise! data :project/wait nil)
        (raise! data :project/data {:user user :repo repo})
        (GET (str "/cached/" user "/" repo ".edn")
          {:handler (partial handler data)
           :error-handler
           (fn [err]
             (if (= 404 (:status err))
               (GET (str "/repo/" user "/" repo)
                 {:handler (partial handler data)
                  :error-handler (partial error-handler data)})
               (error-handler data err)))}))
      (do
        (repo-event! "invalid" url)
        (raise! data :project/invalid-url url)))))

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
      (components/link link))))

(defn overlay [data owner]
  (om/component
    (dom/div #js {:className "overlay"})))

(defn modal [data owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (om/set-state! owner :listen-click-in
        (click/install-out! ["about-modal"]
          #(raise! data :nav/close-help nil))))
    om/IWillUnmount
    (will-unmount [_]
      (click/uninstall! (om/get-state owner :listen-click-in)))
    om/IRender
    (render [_]
      (dom/div #js {:className "modal"}
        (dom/div #js {:className "page center-container"}
          (dom/div #js {:className "about-modal float-box blue-box"}
            (om/build close-button data)
            (dom/h1 #js {:className "blue-box__title"} "About")
            (dom/p nil "Dependency graphs can help you comunicate
                    with your teammates, introduce people to the codebase,
                    explore changes to the architecture, and help you
                    enforce it during reviews.")
            (dom/p nil "Code analysis can be "
              (components/link "https://www.youtube.com/watch?v=hWhBmJJZoNM"
                "very useful" "file--activate")
                   "but it is inconvienient to setup and generally ignored.
                    Asterion aims to make code analysis more approachable.
                    Are you interested in graphing "
              (components/forms-link "private repositories?"))
            (dom/p nil "To use the tool, make sure you are pasting a link to a
                    Github repository that contains a project.clj file in
                    it's top directory. For example:")
            (components/link "https://github.com/juxt/yada"
              "https://github.com/juxt/yada" "file--activate")
            (dom/p nil "will work, but "
              (components/link "https://github.com/clojure/clojure"
                "https://github.com/clojure/clojure")
              "won't because it doesn't have a project.clj. Boot projects are
               not yet supported.")
            (dom/p nil "If you have any feedback, you can find me at "
              (components/link "mailto:sbensu@gmail.com" "sbensu@gmail.com" "file--activate"))))))))

(defn select-project [data owner]
  (om/component
    (dom/div #js {:className "page center-container whole-page"}
      (when (:overlay? data)
        (om/build overlay data))
      (when (:overlay? data)
        (om/build modal data))
      (om/build help-button data)
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
        (if (:waiting? data)
          (dom/div nil
            (dom/p nil (str "Processing " (or (project-name data) "...")))
            (dom/div #js {:className "loader"})
            (dom/p nil "Try other cool projects:")
            (apply dom/ul #js {:className "folder-list"}
              (->> examples
                (remove (partial = (:url (:project data))))
                shuffle
                (take 3)
                (map (partial om/build example-link))))
            (dom/p nil
              (dom/span nil "but whatever you do, don't try "
                (components/link "https://github.com/clojure/core.typed"
                                 "core.typed"))))
          (dom/div nil
            (dom/p nil "Make dependency graphs for Clojure projects.")
            (dom/p nil "Paste a link for a github repo:")
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
    (dom/input #js {:className "top-break blue-input"
                    :type "text"
                    :title title
                    :placeholder placeholder
                    :value (get data value-key)
                    :onKeyDown (fn [e]
                                 (when (= "Enter" (.-key e))
                                   (on-enter e)))
                    :onChange (fn [e]
                                (on-change (.. e -target -value)))})))

(defn build-item [{:keys [data label]} owner]
  (om/component
    (dom/li #js {:onClick (fn [_]
                            (raise! data :nav/new-build label)
                            (raise! data :nav/toggle-builds nil))
                 :className "build-item"}
      (dom/span #js {:className "build-name"} label))))

(defn nav [data owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "float-box--side blue-box nav"}
        (if (empty? (:repo (:project data)))
          (dom/h3 #js {:className "blue-box__title"} "Asterion")

          (dom/div nil
            (dom/h3 #js {:className "project-name"} (:repo (:project data)))
            (when (< 1 (count (:graphs data)))
              (when-let [build (:build (:nav data))]
                (dom/div nil
                  (dom/span #js {:className "build-title build-name"
                                 :onClick (fn [e]
                                            (raise! data :nav/toggle-builds nil))}
                    (if (:open? (:nav data))
                      (dom/i #js {:className "menu-icon fa fa-caret-down"})
                      (dom/i #js {:className "menu-icon fa fa-caret-right"}))
                    build)
                  (when (:open? (:nav data))
                    (apply dom/ul #js {:className "builds"}
                      (->> (keys (:graphs data))
                        (remove (partial = build))
                        (map #(om/build build-item {:data data
                                                    :label %}))))))))))
        (om/build clear-button data)
        (om/build nav-input (:nav data)
          {:opts {:on-change (partial raise! data :nav/ns)
                  :on-enter (fn [e]
                              (nav-event! "filter" (.. e -target -value))
                              (raise! data :nav/draw! nil))
                  :title "When you press Enter, I'll remove the ns with names that match these words."
                  :value-key :ns
                  :placeholder "filter out namespaces"}})))))

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
        (let [vsm (ViewportSizeMonitor.)]
          (om/set-state! owner :vsm vsm)
          (events/listen vsm events/EventType.RESIZE (fn [e] (draw!))))
        (draw!))
      om/IWillUnmount
      (will-unmount [_]
        (.unlisten (om/get-state owner :vsm) events/EventType.RESIZE)))))

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
    (if (empty? (:graphs data))
      (om/build select-project data)
      (om/build graph-explorer data))))

(defn init! []
  (om/root main app-state {:target (.getElementById js/document "app")}))
