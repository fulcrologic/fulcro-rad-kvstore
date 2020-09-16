(ns play.konserve.start-riak
  (:require [au.com.seasoft.general.dev :as dev]))

(defn example-f [& {:keys [conn-url]}]
  (dev/log-on "got" conn-url))

(defn x-1 []
  (example-f :conn-url "some url"))
