(ns user
  (:require
    [clojure.pprint :refer [pprint]]
    [mount.core :as mount]
    [com.example.components.server]
    [clojure.stacktrace :as st]
    [clojure.tools.namespace.repl :as tools-ns :refer [set-refresh-dirs]]
    [clojure.stacktrace :as st]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

;; S/how the refer is not seen
(defn print-stack-trace [one two]
  (st/print-cause-trace one two))

(set-refresh-dirs "dev" "src/main" "src/play")

(defn refresh [& args]
  (tools-ns/refresh))

(defn- start []
  (mount/start))

(defn stop
  "Stop the server."
  []
  (mount/stop))

(defn go
  "Initialize the server and start it."
  ([] (go :dev))
  ([path]
   (start)))

(defn restart
  []
  (stop)
  (start))