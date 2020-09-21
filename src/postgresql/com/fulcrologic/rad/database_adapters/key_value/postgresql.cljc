(ns com.fulcrologic.rad.database-adapters.key-value.postgresql
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [konserve-pg.core :refer [new-pg-store]]))

(defmethod kv-key-store/make-adaptor :postgresql
  [_ {:postgresql/keys [db]}]
  [(str "Konserve PostgreSQL at " db)
   (new-pg-store db)])
