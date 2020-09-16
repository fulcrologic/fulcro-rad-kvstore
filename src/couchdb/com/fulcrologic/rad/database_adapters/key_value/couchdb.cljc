(ns com.fulcrologic.rad.database-adapters.key-value.couchdb
  (:require
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [konserve-clutch.core :refer [new-clutch-store]]))

(defmethod key-value/make-konserve-adaptor :couchdb
  [_ {:couchdb/keys [db]}]
  [(str "Konserve CouchDB at " db)
   (new-clutch-store db)])
