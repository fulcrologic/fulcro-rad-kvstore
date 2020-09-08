(ns com.fulcrologic.rad.database-adapters.key-value.adaptor
  "This Key Value store adaptor library reads/writes data from/to a KeyStore, where a KeyStore is a supported database.
  So far there are KeyStore implementations for 'clojure atom' and Redis. Base data is always stored with key an
  `eql/ident` and the value a map of attributes, where the attribute values are either scalars or references. A
  reference can be either an ident or a vector of idents. If the data you want to store is not already strictly
  normalised in this way then see the function `::kv-write/write-tree`.

  Creation examples:

    ks (memory-adaptor/->MemoryKeyStore \"human consumption identifier\" (atom {}) {})
    adaptor (redis-adaptor/->RedisKeyStore {:pool {} :spec {:uri \"redis://127.0.0.1:6379/\"}} {:key-value/table-kludge? true})

  However you wouldn't create directly like this if using from RAD. Instead set one of your databases to be the `:main`
  one in a configuration file (defaults.edn in the example project) and have a mount `defstate` that calls
  `::kv-database/start`.

  The most primitive way to read is using the protocol directly. `::read1` returns what is stored at the ident - a
  single entity map where all references are idents. `::read*` is the plural version. `::read-table` accepts a
  table keyword and returns all the rows in the table as a vector of idents. `::read-table` works well with
  `::kv-entity-read/read-tree`. For example:

    (let [read-tree (kv-entity-read/read-tree-hof db env)]
      (->> (kv-adaptor/read-table db env :account/id)
           (map read-tree)
           (filter :account/active?)
           (mapv #(select-keys % [:account/id]))))

  In this namespace references that are input and output are always idents."
  (:require [edn-query-language.core :as eql]
            [com.fulcrologic.rad.database-adapters.key-value :as key-value]
            [com.fulcrologic.guardrails.core :refer [>defn => ?]]
            [clojure.spec.alpha :as s]))

(>defn cardinality
  "Is the arg a single ident, many idents or a keyword that represents a table? Returns `:ident`, `:idents` or `:table`"
  [ident-or-idents-or-table]
  [::key-value/ident-s-or-table => keyword?]
  (cond
    (eql/ident? ident-or-idents-or-table) :ident
    (keyword? ident-or-idents-or-table) :table
    (seq ident-or-idents-or-table) :idents))

;;
;; Will have only one implementation (Konserve), and we will replace it with a map
;;
(defprotocol KeyStoreK
  (-k-instance-name [this])
  (-k-options [this])
  (-k-store [this]))

(defn key-store? [x]
  (satisfies? KeyStoreK x))

(s/def ::key-store key-store?)

(>defn instance-name
  "A human readable identifier for this KeyStore. This identifier was set at the time of `KeyStore` creation"
  [this]
  [::key-store => string?]
  (-k-instance-name this))

(>defn options
  "Options (all boolean so far) that were set at the time of `KeyStore` creation"
  [this]
  [::key-store => map?]
  (-k-options this))

(>defn store
  "Return the store that can read and write from. Only Konserve needs to call this"
  [this]
  [::key-store => any?]
  (-k-store this))
