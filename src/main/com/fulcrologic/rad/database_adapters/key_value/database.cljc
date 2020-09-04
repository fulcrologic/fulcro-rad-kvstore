(ns com.fulcrologic.rad.database-adapters.key-value.database
  "A namespace for anything to do with the whole database, for instance importing and exporting..."
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.memory :as memory-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.redis :as redis-adaptor]))

(>defn start
  "Returns a map containing only one database - the `:main` one.
  Not like the datomic implementation of the same function, that will return many databases.
  So many databases can be in the config file, and switch one of them to be `:main`
  Also (unlike the datomic implementation) note that attributes are not passed in because there is no schema with Key
  Value databases, hence no automatic schema generation is possible"
  [{::key-value/keys [databases]}]
  [map? => map?]
  (let [{:key-value/keys [kind table-kludge? dont-store-nils?]
         :or             {table-kludge? false dont-store-nils? false}
         :as             main-database} (:main databases)]
    (when (nil? main-database)
      (throw (ex-info "Need to have a database called :main" {:names (keys databases)})))
    (when (nil? kind)
      (throw (ex-info ":kind not found in :main database\n" {:database main-database})))
    {:main (case kind
             :clojure-atom (memory-adaptor/->MemoryKeyStore "MemoryKeyStore" (atom {}) main-database)
             :redis (let [{:redis/keys [uri]} main-database
                          conn {:pool {} :spec {:uri uri}}]
                      (redis-adaptor/->RedisKeyStore conn main-database)))}))

;;
;; Later we can export to an edn file then import back in
;;
(>defn export
  "Sometimes useful to see the whole database at once"
  [db env tables]
  [::kv-adaptor/key-store map? ::key-value/tables => vector?]
  (into {} (for [table tables]
             (let [entities (->> (kv-adaptor/read-table db env table)
                                 (mapv (partial kv-adaptor/read1 db env)))]
               [table entities]))))

(>defn wipe
  [db tables]
  [::kv-adaptor/key-store ::key-value/tables => any?]
  (doseq [table tables]
    (kv-write/remove-table-rows! db {} table)))