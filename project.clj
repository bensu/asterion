(defproject draft "0.1.0-alpha1"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/cljs"]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [cljsjs/d3 "3.5.5-3"]
                 [org.omcljs/om "0.9.0"]]  

  :plugins [[lein-cljsbuild "1.1.0"]]

  :min-lein-version "2.5.1"

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "app/js/p/app.js"
                                        :output-dir    "app/js/p/out"
                                        :asset-path    "js/p/out"
                                        :optimizations :none
                                        :pretty-print  true
                                        :cache-analysis true}}}}

  :clean-targets ^{:protect false} [:target-path "out" "app/js/p"]

  :figwheel {:css-dirs ["app/css"]}

  :profiles
  {:dev {:cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                    :compiler {:source-map true
                                               :main       "draft.dev"
                                               :verbose true}
                                    :figwheel {:on-jsload "draft.core/mount-root"}}}}

         :plugins [[lein-ancient "0.6.7"]
                   [lein-kibit "0.1.2"]
                   [lein-cljfmt "0.3.0"]
                   [lein-figwheel "0.3.8"]]}
   :production
   {:cljsbuild
    {:builds {:app {:compiler {:optimizations :advanced
                               :main          "draft.prod"
                               :cache-analysis false
                               :closure-defines {:goog.DEBUG false}
                               :externs ["node_modules/closurecompiler-externs/path.js"
                                         "node_modules/closurecompiler-externs/fs.js"
                                         "externs/misc.js"]
                               :pretty-print false}
                    :source-paths ["env/prod/cljs"]}}}}})
