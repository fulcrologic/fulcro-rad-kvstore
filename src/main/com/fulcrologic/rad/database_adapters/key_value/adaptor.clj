(ns com.fulcrologic.rad.database-adapters.key-value.adaptor
  "Use to read and write data from/to a KeyStore, where a KeyStore is a supported Key Value database. So far there are
  KeyStore implementations for 'clojure atom' and Redis. The key is always an eql/ident and the value is always a map
  of attributes, where the values are either scalars or references. A reference can be either an ident or a vector of
  idents. If the data you want to store is not strictly normalised in this way then see the namespace
  `::key-value/write`.
  Creation examples:
    "
  (:require [edn-query-language.core :as eql]
            [clojure.spec.alpha :as s]))

(defn cardinality [ident-or-idents-or-table]
  (cond
    (eql/ident? ident-or-idents-or-table) :ident
    (keyword? ident-or-idents-or-table) :table
    (seq ident-or-idents-or-table) :idents))

;; TODO: These all need docstrings. The ns would also ideally have a rather large docstring describing general use
;; or at least referring to mem store as reference
(defprotocol KeyStore
  (-instance-name-f [this env])
  (-read* [this env ident-or-idents-or-table])
  (-write* [this env pairs-of-ident-map])
  (-write1 [this env ident m])
  (-remove1 [this env ident]))

(s/def ::key-store #(satisfies? KeyStore %))

;; An ident-like thing
(s/def ::ident-or-map (s/or :ident eql/ident?
                            :ident-like-map (every-pred map? #(= 1 (count %)))))

(s/def ::idents (s/coll-of eql/ident?))

;; Used for fetching data out
(s/def ::ident-or-idents-or-table (s/or :ident eql/ident?
                                        :idents ::idents
                                        :table keyword?))

(defn instance-name-f [this env]
  "A human readable identifier for this KeyStore"
  (-instance-name-f this env))

(defn read* [this env ident-or-idents-or-table]
  "Returns the data associated with what you pass in - either an ident, a sequence of idents or a table keyword"
  (-read* this env ident-or-idents-or-table))

(defn write* [this env pairs-of-ident-map]
  "Submit data to be stored, in the form [[ident {}]...], where ident is [table id]"
  (-write* this env pairs-of-ident-map))

(defn write1 [this env ident m]
  "Submit an item of data to be stored, [ident {}], where ident is [table id]"
  (-write1 this env ident m))

(defn remove1 [this env ident]
  "Remove an ident from storage"
  (-remove1 this env ident))