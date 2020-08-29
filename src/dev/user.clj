(ns user
  (:require
    [clojure.pprint :refer [pprint]]
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
  ;(apply lite-refresh/refresh args)
  (tools-ns/refresh))