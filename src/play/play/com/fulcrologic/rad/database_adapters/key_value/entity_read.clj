(ns play.com.fulcrologic.rad.database-adapters.key-value.entity-read
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.entity-read :as kv-entity-read]
    [com.fulcrologic.rad.database-adapters.key-value.memory :as memory-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]))

(defn x-1 []
  (kv-write/entity->eql-result {:person/id     1
                                :db/id         7
                                :person/alive? true
                                :person/pulse  65}
                               :person/id))

(defn x-2 []
  (kv-entity-read/read-tree
    (memory-adaptor/->MemoryKeyStore "x-2" (atom {}))
    {}
    {:a 1 :b 2}))