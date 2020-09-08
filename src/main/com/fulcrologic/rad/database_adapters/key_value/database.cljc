(ns com.fulcrologic.rad.database-adapters.key-value.database
  "A namespace for anything to do with the whole database, for instance importing and exporting..."
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.konserve :as konserve-adaptor]
    [konserve.filestore :refer [new-fs-store]]
    [konserve.memory :refer [new-mem-store]]
    [konserve-carmine.core :refer [new-carmine-store]]
    [clojure.core.async :as async :refer [<!! chan go go-loop]]
    [clojure.spec.alpha :as s]
    [taoensso.timbre :as log]))

(>defn start
  "Returns a map containing only one database - the `:main` one.
  Not like the datomic implementation of the same function, that will return many databases.
  So many databases can be in the config file, and switch one of them to be `:main`
  Also (unlike the datomic implementation) note that attributes are not passed in because there is no schema with Key
  Value databases, hence no automatic schema generation is possible"
  [{::key-value/keys [databases]}]
  [map? => map?]
  (let [{:key-value/keys [kind dont-store-nils?]
         :or             {dont-store-nils? false}
         :as             main-database} (:main databases)]
    (when (nil? main-database)
      (throw (ex-info "Need to have a database called :main" {:names (keys databases)})))
    (when (nil? kind)
      (throw (ex-info ":kind not found in :main database\n" {:database main-database})))
    {:main (case kind
             :filestore (let [{:filestore/keys [location]} main-database
                              store (<!! (new-fs-store location))]
                          (konserve-adaptor/make-konserve-key-store
                            store (str "Konserve fs at " location) main-database))
             :redis (let [{:redis/keys [uri]} main-database
                          store (<!! (new-carmine-store uri))]
                      (konserve-adaptor/make-konserve-key-store
                        store (str "Konserve redis at " uri) main-database))
             :memory (let [store (<!! (new-mem-store))]
                       (konserve-adaptor/make-konserve-key-store
                         store (str "Konserve memory store") main-database))
             )}))

;;
;; Later we can export to an edn file then import back in
;;
(>defn export
  "Sometimes useful to see the whole database at once"
  [{:keys [read-table]} env tables]
  [::key-value/key-store map? ::key-value/tables => map?]
  (into {} (for [table tables]
             (let [entities (vec (read-table table))]
               [table entities]))))

(>defn destructive-reset
  "Remove the row data for given tables, then write new entities. Usually the new entities are for corresponding tables,
  however they don't have to be"
  ([db tables entities]
   [::key-value/key-store ::key-value/tables (s/coll-of ::key-value/table-id-entity-3-tuple :kind vector?) => any?]
   (doseq [table tables]
     (kv-write/remove-table-rows! db {} table))
   (when entities
     (doseq [[table id value] entities]
       (kv-write/write-tree db {} value)))
   (log/info "Destructively reset" (count tables) "tables, replacing with data from" (count entities)))
  ([db tables]
   [::key-value/key-store ::key-value/tables => any?]
   (destructive-reset db tables [])))