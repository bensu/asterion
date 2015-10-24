(ns asterion.project
  (:import [java.io File]
           [java.util UUID]
           [org.apache.commons.io FileUtils]
           [org.eclipse.jgit.api.errors TransportException
            InvalidRemoteException])
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [leiningen.core.project :as project]
            [clojure.tools.namespace.file :as file]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.dependency :as dep]
            [clj-jgit.porcelain :as git]
            [clj-jgit.util :as git-util]))

;; ====================================================================== 
;; Utils

(defn uuid []
  (str (UUID/randomUUID)))

;; ======================================================================  
;; Helpers

(defn file->ns-name [f]
  (-> f file/read-file-ns-decl rest first))

;; ====================================================================== 
;; Dependency graph 

(defn parse-project
  "Takes a File "
  [^File project-dir]
  (let [f  (io/file project-dir "project.clj")]
    (assert (.exists f) "project.clj not found")
    (->> (.getPath f)
      project/read-raw 
      :source-paths 
      (mapv (partial io/file project-dir)))))

(defn platform->ext [platform]
  (get {:clj find/clj
        :cljs find/cljs
        :cljc (update find/clj :extensions (partial cons ".cljs"))}
    platform
    find/clj))

(defn depgraph
  "Constructs a graph - {:nodes [{:name foo.bar}]
                         :edges [{:source foo.bar :target foo.baz}]}
  from a set of source-paths."
  ([srcs] (depgraph nil srcs))
  ([platform srcs]
   (let [exts (platform->ext platform) 
         source-fs (apply set/union
                     (map #(find/find-sources-in-dir % exts) srcs))
         dep-graph ((file/add-files {} source-fs) ::track/deps)
         ns-names (set (map (comp second file/read-file-ns-decl) source-fs))
         part-of-project? (partial contains? ns-names)
         nodes (filter part-of-project? (reverse (dep/topo-sort dep-graph)))
         edges (->> nodes
                 (mapcat #(->> (filter part-of-project?
                                 (dep/immediate-dependencies dep-graph %))
                            (map (partial vector %)))))]
     {:edges (mapv (fn [[from to]] {:source from :target to}) edges)
      :nodes (->> nodes
               (map-indexed (fn [i n] [n i]))
               (into {})
               (sort-by second)
               (mapv (fn [[n i]] {:name (str n)})))})))

(defn parse-url
  "Given a git url (string) it will fetch the repository and try to return
   a dependency graph for it. It may also return the following errors:

  - {:error :no-project-file}
  - {:error :project-not-found}
  - {:error :project-protected}
  - {:error #<Exception>} ;; unknown exception"
  [url]
  {:pre [(string? url)]}
  (let [repo-name (git-util/name-from-uri url)
        dir (io/file "tmp" (str (uuid) repo-name))]
    (try
      (git/git-clone-full url (.getPath dir))
      (depgraph (parse-project dir))
      (catch java.lang.AssertionError _
        {:error :project/no-project-file})
      (catch InvalidRemoteException _
        {:error :project/not-found})
      (catch TransportException _
        {:error :project/protected})
      (catch Exception e
        {:error e})
      (finally (future (FileUtils/deleteDirectory dir))))))
