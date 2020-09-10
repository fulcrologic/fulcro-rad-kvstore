(ns com.fulcrologic.rad.database-adapters.key-value.adaptor
  "The 'adaptor' is the value of the key `:store` in ::key-value/key-store (a simple map). The specs are
  at ::key-value. We create the simple map here. Also other existential things like filling with data or getting all
  the data out. The word 'store' is what Konserve always uses in its documentation. It is their adaptor we are using"
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [konserve.core :as k]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [konserve.filestore :refer [new-fs-store]]
    [konserve.memory :refer [new-mem-store]]
    [konserve-carmine.core :refer [new-carmine-store]]
    [clojure.core.async :as async :refer [<!! <! >! close! chan go go-loop]]
    [clojure.spec.alpha :as s]
    [taoensso.timbre :as log]
    [com.fulcrologic.rad.database-adapters.strict-entity :as strict-entity]))

;;
;; There are supposed to be bang versions
;; https://github.com/replikativ/konserve/issues/24
;; So not need to <!! here every time
;; Not documented in Konserve apart from a resolved issue.
;;
;; These functions are inside a ::key-value/key-store. That's why they are special and take a ::key-value/store
;; as an arg. Normally always pass around a ::key-value/key-store and de-structure to a store or one of these
;; functions
;;

(>defn table->rows
  "Get all the data for a particular table as a vector of maps"
  [store table]
  [::key-value/store ::strict-entity/table => ::key-value/rows]
  (vec (vals (<!! (k/get-in store [table])))))

(>defn table->ident-rows
  "Instead of getting all the data, get all the rows but only in ident form"
  [store table]
  [::key-value/store ::strict-entity/table => ::key-value/idents]
  (->> (keys (<!! (k/get-in store [table])))
       (mapv (fn [id] [table id]))))

(>defn ident->entity
  "Given an ident, fetch out a whole entity"
  [store ident]
  [::key-value/store ::strict-entity/ident => ::key-value/entity]
  (<!! (k/get-in store ident)))

(>defn write-entity
  "Store an entity at its ident location"
  [store entity]
  [::key-value/store ::key-value/entity => any?]
  (<!! (k/assoc-in store (strict-entity/entity->ident entity) entity)))

(>defn ids->entities
  "Getting a load of entities from idents at once (async) and putting them into an output chan"
  [store table ids]
  [::key-value/store ::strict-entity/table ::key-value/ids => vector?]
  (let [out (chan (count ids))]
    (go
      (loop [ids ids]
        (when-let [id (first ids)]
          (let [entity (<! (k/get-in store [table id]))]
            (if entity
              (>! out entity)
              (log/warn "Could not find an entity from ident" [table id]))
            (recur (next ids)))))
      (close! out))
    (<!! (async/into [] out))))

(comment
  "Destructure like this and pass `store` to any k/ function"
  {:keys [store table->rows table->ident-rows ident->entity write-entity ids->entities]})

(>defn make-key-store
  "Given a Konserve key value store (the real adaptor) create a map around it so we have access to its instance-name and
  options. Additionally we include some functions for common tasks. Full destructuring would be:
   `{:keys [store table->rows table->ident-rows ident->entity write-entity ids->entities]}`
  Pass store to any `k/` functions. Otherwise always pass a ::key-value/key-store around"
  [store instance-name options]
  [::key-value/store string? map? => ::key-value/key-store]
  {:store store
   :instance-name instance-name
   :options options
   :table->rows (partial table->rows store)
   :table->ident-rows (partial table->ident-rows store)
   :ident->entity (partial ident->entity store)
   :write-entity (partial write-entity store)
   :ids->entities (partial ids->entities store)})

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
             :filestore (let [{:filestore/keys [location]} main-database]
                          (make-key-store
                            (<!! (new-fs-store location))
                            (str "Konserve file store at " location)
                            main-database))
             :redis (let [{:redis/keys [uri]} main-database]
                      (make-key-store
                        (<!! (new-carmine-store uri))
                        (str "Konserve Redis at " uri)
                        main-database))
             :memory (make-key-store
                       (<!! (new-mem-store))
                       (str "Konserve memory store")
                       main-database)
             )}))

(>defn export
  "Sometimes useful to see the whole database at once"
  [{:keys [read-table]} env tables]
  [::key-value/key-store map? ::key-value/tables => map?]
  (into {} (for [table tables]
             (let [entities (vec (read-table table))]
               [table entities]))))

(>defn import
  "Remove the row data for given tables, then write new entities. Usually the new entities are for corresponding tables,
  however they don't have to be"
  ([key-store tables entities]
   [::key-value/key-store ::key-value/tables (s/coll-of ::key-value/table-id-entity-3-tuple :kind vector?) => any?]
   (doseq [table tables]
     (kv-write/remove-table-rows! key-store {} table))
   (when entities
     (doseq [[table id value] entities]
       (kv-write/write-tree key-store value)))
   (log/info "Have destructively reset" (count tables) "tables, replacing with data from" (count entities) "entities"))
  ([key-store tables]
   [::key-value/key-store ::key-value/tables => any?]
   (import key-store tables [])))