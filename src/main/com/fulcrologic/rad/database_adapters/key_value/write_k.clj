(ns com.fulcrologic.rad.database-adapters.key-value.write-k
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [konserve.core :as k]
    [clojure.core.async :as async :refer [<!! chan go go-loop]]
    [general.dev :as dev]))

(>defn write-tree
  "Writing will work whether given denormalized or normalized. Use this function to seed/import large amounts of
  data. As long as the input is coherent all the references should be respected. See
  `com.example.components.seeded-connection/seed!` for example usage."
  [ks env m]
  [::kv-adaptor/key-store map? map? => any?]
  (let [store (kv-adaptor/store ks)
        entries (kv-write/flatten m)]
    (<!! (go-loop [entries entries]
           (when-let [[ident m] (first entries)]
             (k/assoc-in store ident m)
             (recur (rest entries)))))))

(>defn remove-table-rows!
  "Given a table find out all its rows and remove them"
  [ks env table]
  [::kv-adaptor/key-store map? ::key-value/table => any?]
  (<!! (k/dissoc (kv-adaptor/store ks) table)))

