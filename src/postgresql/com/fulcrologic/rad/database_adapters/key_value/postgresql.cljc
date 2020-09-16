(ns com.fulcrologic.rad.database-adapters.key-value.postgresql
  (:require
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [konserve-pg.core :refer [new-pg-store]]))

(defmethod key-value/make-konserve-adaptor :postgresql
  [_ {:postgresql/keys [db]}]
  [(str "Konserve PostgreSQL at " db)
   (new-pg-store db)])
