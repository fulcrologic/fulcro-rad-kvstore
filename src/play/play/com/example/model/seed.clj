(ns play.com.example.model.seed
  (:require [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
            [general.dev :as dev]
            [com.fulcrologic.rad.database-adapters.key-value.write :as key-value-write :refer [ident-of value-of]]
            [com.example.model.seed :as seed]
            [com.fulcrologic.rad.database-adapters.key-value.memory :as memory-adaptor]
            [com.fulcrologic.rad.ids :refer [new-uuid]]))

(def pathom-env {})

#_(defn write-and-read-3 []
  (let [ks (memory-adaptor/->MemoryKeyStore "x-3" (atom {}))
        barb (seed/new-account (new-uuid 103) "Barbara" "barb@example.com" "letmein")]
    (key-value-write/write-tree ks pathom-env (value-of barb))
    (dev/log-on "Just written:")
    (dev/pp (kv-adaptor/db-f ks pathom-env))
    (kv-adaptor/write1 ks pathom-env (ident-of barb) {:account/active? false})
    (dev/log-on "Now after have made inactive:")
    (dev/pp (kv-adaptor/db-f ks pathom-env))
    ))
