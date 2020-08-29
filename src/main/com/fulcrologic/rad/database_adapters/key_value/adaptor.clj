(ns com.fulcrologic.rad.database-adapters.key-value.adaptor
  (:require [edn-query-language.core :as eql]))

(defn cardinality [ident-or-idents-or-table]
  (cond
    (eql/ident? ident-or-idents-or-table) :ident
    (keyword? ident-or-idents-or-table) :keyword
    (seq ident-or-idents-or-table) :idents))

;; TODO: These all need docstrings. The ns would also ideally have a rather large docstring describing general use
;; or at least referring to mem store as reference
(defprotocol KeyStore
  (instance-name-f [this env])
  (db-f [this env])
  (read* [this env ident-or-idents-or-table] "Read ...")
  (write* [this env pairs-of-ident-map])
  (write1 [this env ident m])
  (remove1 [this env ident]))