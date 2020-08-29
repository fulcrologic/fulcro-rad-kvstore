(ns com.fulcrologic.rad.database-adapters.key-value.adaptor
  (:require [edn-query-language.core :as eql]))

(defn cardinality [ident-or-idents-or-table]
  (cond
    (eql/ident? ident-or-idents-or-table) :ident
    (keyword? ident-or-idents-or-table) :keyword
    (seq ident-or-idents-or-table) :idents))

(defprotocol KeyStore
  (instance-name-f [this env])
  (db-f [this env])
  (read* [this env ident-or-idents-or-table])
  (write* [this env pairs-of-ident-map])
  (write1 [this env ident m])
  (remove1 [this env ident]))