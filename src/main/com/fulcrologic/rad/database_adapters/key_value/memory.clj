(ns com.fulcrologic.rad.database-adapters.key-value.memory
  "A reference implementation of `::kv-adaptor/KeyStoreH` that uses a Clojure atom as the database"
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]))

(defn- feed-pair [st pair]
  (let [[ident m] (if (map? pair)
                    (first pair)
                    pair)]
    (update st ident merge m)))

(defn- write!
  [a pairs-of-ident-map]
  (swap!-> a
           ((fn [st]
              (reduce
                feed-pair
                st
                (cond
                  ((every-pred seq (complement map?)) pairs-of-ident-map) pairs-of-ident-map
                  (map? pairs-of-ident-map) (into [] pairs-of-ident-map)))))))

(defn table-idents
  "We preference read-tree by always returning idents"
  [m table]
  (->> m
       keys
       (filterv #(= table (first %)))))

(deftype MemoryKeyStore [keystore-name a options] kv-adaptor/KeyStoreH
  (-read* [this env idents]
    (mapv (fn [ident] (get @a ident)) idents))
  (-read1 [this env ident]
    (get @a ident))
  (-read-table [this env table]
    (table-idents @a table))
  (-write* [this env pairs-of-ident-map]
    (write! a pairs-of-ident-map))
  (-write1 [this env ident m]
    (write! a [[ident m]]))
  (-remove1 [this env ident]
    (swap! a dissoc ident))
  (-instance-name [this]
    keystore-name)
  (-options [this]
    options)
  (-store [this]
    (throw (ex-info "Can't do -store" {}))))