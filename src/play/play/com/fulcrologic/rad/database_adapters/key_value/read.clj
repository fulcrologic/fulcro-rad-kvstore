(ns play.com.fulcrologic.rad.database-adapters.key-value.read
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.read :as key-value-read]
    [com.fulcrologic.rad.database-adapters.key-value.memory :as memory-adaptor]))

(defn x-1 []
  (key-value-read/map->eql-result {:person/id     1
                                   :db/id         7
                                   :person/alive? true
                                   :person/pulse  65}
                                  :person/id))

(defn x-2 []
  (key-value-read/read-tree (memory-adaptor/->MemoryKeyStore "x-2" (atom {})) {} {:a 1 :b 2}))