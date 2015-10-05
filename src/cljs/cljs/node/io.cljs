(ns cljs.node.io
  "Provides file utilities"
  (:require [cljs.nodejs]))

(def path (js/require "path"))

(def fs (js/require "fs"))

(defn file->folder [p]
  (path.dirname p))

(defn join-paths [& args]
  (apply path.join args))

(defn list-files [dir]
  (mapv (partial join-paths dir) (into-array (fs.readdirSync dir))))

(defn dir? [d]
  (try
    (.isDirectory (fs.lstatSync d))
    (catch js/Object _ false)))

(defn file-name [p]
  (.-name (path.parse p)))

(defn list-dirs [dir]
  (vec (filter dir? (list-files dir))))

(defn read-file [file]
  (fs.readFileSync file "utf8"))
