(ns com.fulcrologic.rad.database-adapters.key-value.redis
  (:require
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [konserve-carmine.core :refer [new-carmine-store]]))

(defmethod key-value/make-konserve-adaptor :redis
  [_ {:redis/keys [uri]}]
  [(str "Konserve Redis at " uri)
   (new-carmine-store)])
