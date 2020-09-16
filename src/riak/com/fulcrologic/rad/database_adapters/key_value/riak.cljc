(ns com.fulcrologic.rad.database-adapters.key-value.riak
  (:require
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [konserve-welle.core :refer [new-welle-store]]))

(defmethod key-value/make-konserve-adaptor :riak
  [_ {:riak/keys [conn-url]}]
  [(str "Konserve Riak at " conn-url)
   (new-welle-store :conn-url conn-url)])
