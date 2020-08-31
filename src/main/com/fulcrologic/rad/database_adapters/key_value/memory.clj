(ns com.fulcrologic.rad.database-adapters.key-value.memory
  "A reference implementation of ::kv-adaptor/KeyStore that uses a Clojure atom as the database"
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [general.dev :as dev]))

(defn- feed-pair [st pair]
  (let [[ident m] (if (map? pair)
                    (first pair)
                    pair)]
    (update st ident merge m)))

(defn- inner-write
  [a env pairs-of-ident-map]
  (swap!-> a
           ((fn [st]
              (reduce
                feed-pair
                st
                (cond
                  ((every-pred seq (complement map?)) pairs-of-ident-map) pairs-of-ident-map
                  (map? pairs-of-ident-map) (into [] pairs-of-ident-map)))))))

(>defn batch-of-rows
  "We preference EQL/Pathom by always returning maps rather than idents"
  [m table]
  [map? keyword? => vector?]
  (->> m
       keys
       (filter #(= table (first %)))
       (mapv (fn [[table id]] {table id}))))

(deftype MemoryKeyStore [keystore-name a] kv-adaptor/KeyStore
  (-instance-name-f [this] keystore-name)
  (-read* [this env idents]
    (mapv (fn [ident] (get @a ident)) idents))
  (-read1 [this env ident]
    (get @a ident))
  (-read-table [this env table]
    (batch-of-rows @a table))
  (-write* [this env pairs-of-ident-map]
    (inner-write a env pairs-of-ident-map))
  (-write1 [this env ident m]
    (inner-write a env [[ident m]]))
  (-remove1 [this env ident]
    (swap! a dissoc ident)))