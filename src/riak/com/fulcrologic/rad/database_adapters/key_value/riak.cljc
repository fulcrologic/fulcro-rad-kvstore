(ns com.fulcrologic.rad.database-adapters.key-value.riak
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [konserve-welle.core :refer [new-welle-store]]))

(defmethod kv-key-store/make-adaptor :riak
  [_ {:riak/keys [conn-url]}]
  [(str "Konserve Riak at " conn-url)
   (new-welle-store :conn-url conn-url)])
