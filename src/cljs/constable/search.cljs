(ns constable.search)

(def results {})

(def ipc (js/require "ipc"))

(.on ipc "search-success"
  (fn [fs]
    (.log js/console fs)))

(defn ^:export isSearched [phrases ns-str]
  {:pre [(string? ns-str)]}
  (true? (some #(contains? (get results %) ns-str) (into-array phrases))))
