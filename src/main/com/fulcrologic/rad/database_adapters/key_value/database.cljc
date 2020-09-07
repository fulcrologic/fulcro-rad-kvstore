(ns com.fulcrologic.rad.database-adapters.key-value.database
  "A namespace for anything to do with the whole database, for instance importing and exporting..."
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
    [com.fulcrologic.rad.database-adapters.key-value.write-k :as kv-write-k]
    [com.fulcrologic.rad.database-adapters.key-value.entity-read :as kv-entity-read]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.memory :as memory-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.redis :as redis-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.konserve :as konserve-adaptor]
    [konserve.filestore :refer [new-fs-store]]
    [konserve.memory :refer [new-mem-store]]
    [konserve-carmine.core :refer [new-carmine-store]]
    [clojure.core.async :as async :refer [<!! chan go go-loop]]
    [clojure.spec.alpha :as s]
    [taoensso.timbre :as log]))

(def konserve-stores #{:k-filestore :k-redis :k-memory})

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
                      (redis-adaptor/->RedisKeyStore conn main-database))
             :k-filestore (let [{:k-filestore/keys [location]} main-database
                                store (<!! (new-fs-store location))]
                            (konserve-adaptor/->KonserveKeyStore
                              (str "Konserve fs at " location) store main-database))
             :k-redis (let [{:k-redis/keys [uri]} main-database
                                store (<!! (new-carmine-store uri))]
                            (konserve-adaptor/->KonserveKeyStore
                              (str "Konserve redis at " uri) store main-database))
             :k-memory (let [store (<!! (new-mem-store))]
                         (konserve-adaptor/->KonserveKeyStore
                           (str "Konserve memory store") store main-database))
             )}))

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

(>defn destructive-reset
  "Remove the row data for given tables, then write new entities. Usually the new entities are for corresponding tables,
  however they don't have to be"
  ([db tables entities]
   [::kv-adaptor/key-store ::key-value/tables (s/coll-of ::key-value/table-id-entity-3-tuple :kind vector?) => any?]
   (let [kind (:key-value/kind (kv-adaptor/options db))
         [remove-table-rows! write-tree] (if (konserve-stores kind)
                                           [kv-write-k/remove-table-rows! kv-write-k/write-tree]
                                           [kv-write/remove-table-rows! kv-write/write-tree])]
     (doseq [table tables]
       (remove-table-rows! db {} table))
     (when entities
       (doseq [[table id value] entities]
         (write-tree db {} value))))
   (log/info "Destructively reset" (count tables) "tables, replacing with data from" (count entities)))
  ([db tables]
   [::kv-adaptor/key-store ::key-value/tables => any?]
   (destructive-reset db tables [])))

(>defn all
  "Return all entities for a table"
  [db env table]
  [::kv-adaptor/key-store map? ::key-value/table => (s/coll-of map? :kind vector?)]
  (let [read-tree (kv-entity-read/read-tree-hof db env)]
    (->> (kv-adaptor/read-table db env table)
         (mapv read-tree))))