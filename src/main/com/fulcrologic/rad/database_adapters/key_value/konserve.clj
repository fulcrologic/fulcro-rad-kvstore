(ns com.fulcrologic.rad.database-adapters.key-value.konserve
  ""
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

(deftype KonserveKeyStore [keystore-name a options] kv-adaptor/KeyStore
  (-read* [this env idents]
    (throw (ex-info "Can't do -read*" {:idents idents})))
  (-read1 [this env ident]
    (throw (ex-info "Can't do -read1" {:ident ident})))
  (-read-table [this env table]
    (throw (ex-info "Can't do -read-table" {:table table})))
  (-write* [this env pairs-of-ident-map]
    ;(write! a pairs-of-ident-map)
    (throw (ex-info "Can't do -write*" {:pairs-of-ident-map pairs-of-ident-map}))
    )
  (-write1 [this env ident m]
    (throw (ex-info "Can't do -write1" {:ident ident})))
  (-remove1 [this env ident]
    (throw (ex-info "Can't do -remove1" {:ident ident})))
  (-instance-name [this]
    keystore-name)
  (-options [this]
    options)
  (-store [this]
    a))