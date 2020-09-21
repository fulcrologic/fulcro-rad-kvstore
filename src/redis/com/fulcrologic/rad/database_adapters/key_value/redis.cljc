(ns com.fulcrologic.rad.database-adapters.key-value.redis
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [konserve-carmine.core :refer [new-carmine-store]]))

(defmethod kv-key-store/make-adaptor :redis
  [_ {:redis/keys [uri]}]
  [(str "Konserve Redis at " uri)
   (new-carmine-store)])
