(ns com.fulcrologic.rad.database-adapters.key-value.adaptor
  (:require [edn-query-language.core :as eql]
            [clojure.spec.alpha :as s]))

(defn cardinality [ident-or-idents-or-table]
  (cond
    (eql/ident? ident-or-idents-or-table) :ident
    (keyword? ident-or-idents-or-table) :keyword
    (seq ident-or-idents-or-table) :idents))

;; TODO: These all need docstrings. The ns would also ideally have a rather large docstring describing general use
;; or at least referring to mem store as reference
(defprotocol KeyStore
  (-instance-name-f [this env])
  (-db-f [this env])
  (-read* [this env ident-or-idents-or-table] "Read ...")
  (-write* [this env pairs-of-ident-map])
  (-write1 [this env ident m])
  (-remove1 [this env ident]))

(s/def ::key-store #(satisfies? KeyStore %))

(s/def ::ident-or-map (s/or :ident eql/ident?
                            :ident-like-map (every-pred map? #(= 1 (count %)))))

(defn instance-name-f [this env]
  (-instance-name-f this env))

(defn db-f [this env]
  (-db-f this env))

(defn read* [this env ident-or-idents-or-table]
  (-read* this env ident-or-idents-or-table))

(defn write* [this env pairs-of-ident-map]
  (-write* this env pairs-of-ident-map))

(defn write1 [this env ident m]
  (-write1 this env ident m))

(defn remove1 [this env ident]
  (-remove1 this env ident))