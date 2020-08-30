(ns com.fulcrologic.rad.database-adapters.key-value.memory
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]))

;; TODO: Public functions should have docstrings
(>defn feed-pair [env st pair]
  [map? map? any? => map?]
  (let [[ident m] (if (map? pair)
                    (first pair)
                    pair)]
    (update st ident merge m)))

(defn- inner-write
  [a env pairs-of-ident-map]
  (let [feed-pair (partial feed-pair env)]
    (swap!-> a
             ((fn [st]
                (reduce
                  feed-pair
                  st
                  (cond
                    ((every-pred seq (complement map?)) pairs-of-ident-map) pairs-of-ident-map
                    (map? pairs-of-ident-map) (into [] pairs-of-ident-map))))))))

;;
;; read-outer* allows you to get a completely denormalized tree from an ident
;; See read-tree
;; However we still preference EQL/Pathom by always returning a map
;;
(defn batch-of-rows [this env table]
  (->> (kv-adaptor/db-f this env)
       keys
       (filter #(= table (first %)))
       (mapv (fn [[table id]] {table id}))))

(deftype MemoryKeyStore [keystore-name a] kv-adaptor/KeyStore
  (-db-f [this env] (deref a))
  (-instance-name-f [this env] keystore-name)
  (-read* [this env ident-or-idents-or-table]
    (let [cardinality (kv-adaptor/cardinality ident-or-idents-or-table)]
      (case cardinality
        :ident (get (kv-adaptor/db-f this env) ident-or-idents-or-table)
        :keyword (batch-of-rows this env ident-or-idents-or-table)
        :idents (mapv (fn [ident]
                        (get (kv-adaptor/db-f this env) ident))
                      ident-or-idents-or-table))))
  (-write* [this env pairs-of-ident-map]
    (inner-write a env pairs-of-ident-map))
  (-write1 [this env ident m]
    (inner-write a env [[ident m]]))
  (-remove1 [this env ident]
    (swap! a update dissoc ident)))