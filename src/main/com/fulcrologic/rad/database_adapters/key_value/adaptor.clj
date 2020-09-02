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
  `::kv-database/start-database`.

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

(defprotocol KeyStore
  (-read-table [this env table])
  (-read* [this env idents])
  (-read1 [this env ident])
  (-write* [this env pairs-of-ident-map])
  (-write1 [this env ident m])
  (-remove1 [this env ident])
  (-instance-name [this])
  (-options [this]))

(defn key-store? [x]
  (satisfies? KeyStore x))

(s/def ::key-store key-store?)

(defn read*
  "Returns the entities associated with the sequence of idents asked for. Is not recursive, so just returns what is at
  the locations, with join values as idents or vectors of idents. A plural version of `::read1`. Both `::read1` and
  `::read*` are used internally by `::kv-entity-read/read-tree`"
  [this env idents]
  (-read* this env idents))

(>defn read1
  "Returns the entity associated with an ident. Not recursive, so just returns what is at the ident's location,
  with join values as idents or vectors of idents. See `::kv-entity-read/read-tree` for a recursive way to read
  entities from the Key Value store.
  Example use. Here the customer id is being found:

    (-> (kv-adaptor/read1 db env [:invoice/id invoice-id])
        :invoice/customer
        second)"
  [this env ident]
  [::key-store map? ::key-value/ident => map?]
  (-read1 this env ident))

(>defn read-table
  "Returns the rows associated with a table keyword in the form of a vector of idents.
  Usually used in conjunction with `::kv-entity-read/read-tree(-hof)`, which takes an ident as input.
  For example:

    (let [read-tree (kv-entity-read/read-tree-hof db env)]
      (->> (kv-adaptor/read-table db env :account/id)
           (map read-tree)
           (filterv :account/active?)))

  In the (relatively rare) case where your resolver requires all the rows of the table you will need to morph the
  output to be what Pathom is expecting, a collection of `::key-value/ident-like-map`
  For example:

    (->> (kv-adaptor/read-table db env :category/id)
         (mapv (fn [[table id]] {table id})))

  This operation is not what Key Value stores meant for, in theory. If you have not set `:key-value/table-kludge?` to
  true then this operation will not be supported (`MemoryKeyStore` excepted). In the future this function will likely be
  replaced with another/others that are more 'list' rather than entity centric, and allow the library user to be more
  involved."
  [this env table]
  [::key-store map? ::key-value/id-keyword => (s/coll-of ::key-value/ident :kind vector?)]
  (-read-table this env table))

(>defn write*
  "Submit entities to be stored, in the form [[ident entity]...], where ident is [table id] and the entity is a map"
  [this env pairs-of-ident-map]
  [::key-store map? ::key-value/pairs-of-ident-map => any?]
  (-write* this env pairs-of-ident-map))

(>defn write1
  "Submit an entity to be stored, [ident entity], where ident key is [table id] and the entity is a map"
  [this env ident m]
  [::key-store map? ::key-value/ident map? => any?]
  (-write1 this env ident m))

(>defn remove1
  "Remove an ident from storage"
  [this env ident]
  [::key-store map? ::key-value/ident => any?]
  (-remove1 this env ident))

(defn instance-name
  "A human readable identifier for this KeyStore. This identifier was set at the time of `KeyStore` creation"
  [this]
  (-instance-name this))

(defn options
  "Options (all boolean so far) that were set at the time of `KeyStore` creation"
  [this]
  (-options this))
