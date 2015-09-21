(ns draft.search)

(def results {})

(defn ^:export isSearched [phrases ns-str]
  {:pre [(string? ns-str)]}
  (true? (some #(contains? (get results %) ns-str) (into-array phrases))))
