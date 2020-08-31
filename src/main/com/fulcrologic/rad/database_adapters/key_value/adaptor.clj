(ns com.fulcrologic.rad.database-adapters.key-value.adaptor
  "Use to read and write data from/to a KeyStore, where a KeyStore is a supported Key Value database. So far there are
  KeyStore implementations for 'clojure atom' and Redis. The key is always an eql/ident and the value is always a map
  of attributes, where the values are either scalars or references. A reference can be either an ident or a vector of
  idents. If the data you want to store is not strictly normalised in this way then see the namespace
  `::kv-write`. Similarly for reading out data in tree form see `::kv-entity-read`.
  Creation examples:
    ks (memory-adaptor/->MemoryKeyStore \"human consumption identifier\" (atom {}))
    adaptor (redis-adaptor/->RedisKeyStore {:pool {} :spec {:uri \"redis://127.0.0.1:6379/\"}} true)"
  (:require [edn-query-language.core :as eql]
            [com.fulcrologic.guardrails.core :refer [>defn => ?]]
            [clojure.spec.alpha :as s]))

(defn cardinality
  "Is the arg a single ident, many idents or a keyword that represents a table? Returns `:ident`, `:idents` or `:table`"
  [ident-or-idents-or-table]
  (cond
    (eql/ident? ident-or-idents-or-table) :ident
    (keyword? ident-or-idents-or-table) :table
    (seq ident-or-idents-or-table) :idents))

;; TODO: These all need docstrings. The ns would also ideally have a rather large docstring describing general use
;; or at least referring to mem store as reference
(defprotocol KeyStore
  (-instance-name-f [this])
  (-read-table [this env table])
  (-read* [this env idents])
  (-read1 [this env ident])
  (-write* [this env pairs-of-ident-map])
  (-write1 [this env ident m])
  (-remove1 [this env ident]))

(s/def ::key-store #(satisfies? KeyStore %))

(s/def ::ident-like-map (every-pred map? #(= 1 (count %))))

(s/def ::idents (s/coll-of eql/ident? :kind vector?))

(s/def ::pairs-of-ident-map any?)

(defn instance-name-f
  "A human readable identifier for this KeyStore"
  [this]
  (-instance-name-f this))

(defn read*
  "Returns the entities associated with the sequence of idents asked for. Is not recursive, so just returns what is at
  the locations, with join values as idents or vectors of idents."
  [this env idents]
  (-read* this env idents))

(defn read1
  "Returns the entity associated with an ident. Not recursive, so just returns what is at the ident's location,
  with join values as idents or vectors of idents. See `::kv-entity-read/read-tree` for a recursive way to read
  entities from the Key Value store"
  [this env ident]
  [::key-store map? eql/ident? => ]
  (-read1 this env ident))

(>defn read-table
  "Returns the rows associated with a table keyword in the form of a vector of ident-like maps.
  Note that each entity/row has only one map-entry - that for the id attribute.
  i.e. [{:table/id 1}{:table/id 2}...]. Perfect for returning all the rows of a table from a Pathom resolver.
  This operation is not what Key Value stores are meant for. If you have not set ::key-value/table-kludge? to true
  then this operation will not be supported (MemoryKeyStore excepted)
  Usually used in conjunction with ::kv-entity-read/read-tree-hof (where hof stands for higher order function)"
  [this env table]
  [::key-store map? keyword? => (s/coll-of ::ident-like-map :kind vector?)]
  (-read-table this env table))

(>defn write*
  "Submit entities to be stored, in the form [[ident entity]...], where ident is [table id] and the entity is a map"
  [this env pairs-of-ident-map]
  [::key-store map? ::pairs-of-ident-map => any?]
  (-write* this env pairs-of-ident-map))

(>defn write1
  "Submit an entity to be stored, [ident entity], where ident key is [table id] and the entity is a map"
  [this env ident m]
  [::key-store map? eql/ident? map? => any?]
  (-write1 this env ident m))

(>defn remove1
  "Remove an ident from storage"
  [this env ident]
  [::key-store map? eql/ident? => any?]
  (-remove1 this env ident))