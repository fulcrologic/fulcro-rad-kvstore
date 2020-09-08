(ns com.fulcrologic.rad.database-adapters.key-value.konserve
  ""
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [clojure.core.async :as async :refer [<!! chan go go-loop]]
    [konserve.core :as k]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]))

(defn read-table [store table]
  (vec (vals (<!! (k/get-in store [table])))))

(defn read-table-idents [store table]
  (->> (keys (<!! (k/get-in store [table])))
       (mapv (fn [id] [table id]))))

(defn ident->entity [store ident]
  (<!! (k/get-in store ident)))

(defn write-entity [store entity]
  (<!! (k/assoc-in store (kv-write/entity->ident entity) entity)))

(comment
  "Destructure like this and pass `store` to any k/ function"
  {:keys [store read-table read-table-idents ident->entity write-entity]})

(defn make-konserve-key-store [store instance-name options]
  {:store store
   :instance-name instance-name
   :options options
   :read-table (partial read-table store)
   :read-table-idents (partial read-table-idents store)
   :ident->entity (partial ident->entity store)
   :write-entity (partial write-entity store)
   })