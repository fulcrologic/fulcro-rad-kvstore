(ns com.fulcrologic.rad.database-adapters.key-value.leveldb
  (:require
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [konserve-leveldb.core :refer [new-leveldb-store]]))

(defmethod key-value/make-konserve-adaptor :leveldb
  [_ {:leveldb/keys [path]}]
  [(str "Konserve LevelDB at " path)
   (new-leveldb-store path)])
