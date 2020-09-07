(ns com.fulcrologic.rad.database-adapters.key-value.entity-read
  "Functions that extract all the information from a particular entity, given its ident"
  (:require
    [edn-query-language.core :as eql]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [taoensso.timbre :as log]))

(defn- reading-idents->value-hof
  "reference is an ident or a vector of idents, or a scalar (in which case not a reference)"
  [ks env]
  (fn [reference]
    (cond
      (eql/ident? reference) (case (kv-adaptor/cardinality reference)
                               :ident (kv-adaptor/read1 ks env reference)
                               :idents (do
                                         ;; So read* never ever being called
                                         (throw (ex-info "Trapped code will never be called??" {:reference reference}))
                                         (kv-adaptor/read* ks env reference)))
      (vector? reference) (let [recurse-f (reading-idents->value-hof ks env)]
                            (mapv recurse-f reference))
      :else reference)))

;;
;; TODO
;; Fix recursion (see doc string)
;;
(>defn read-tree
  "Given the starting point of an ident will recursively keep reading the joins, returning the expanded tree.
  Basically there is maximal tree explosion - every attribute on every entity and every reference followed.
  Currently there is no stop on infinite recursion but there should be. Something like stop at 20 times with a
  warning message that suggests to set `recursion-limit` (not yet implemented)."
  [ks env ident]
  [::kv-adaptor/key-store map? ::key-value/ident => (? map?)]
  (let [entity (kv-adaptor/read1 ks env ident)
        idents->value (reading-idents->value-hof ks env)]
    (when entity
      (into {}
            (map (fn [[k v]]
                   (if (nil? v)
                     (do
                       (log/warn "`::kv-entity-read/read-tree` nil value in database for attribute" k)
                       [k v])
                     [k (idents->value v)])))
            entity))))

(>defn read-tree-hof
  "A higher order function (hof) used to return a function that recursively returns as much of the tree as possible,
  when given an ident. Typical usage:
  (let [read-tree (kv-entity-read/read-tree-hof db env)]
    (->> (kv-adaptor/read-table db env :account/id)
         (map read-tree)
         (filter :account/active?)
         (mapv #(select-keys % [:account/id]))))
 "
  [ks env]
  [::kv-adaptor/key-store map? => fn?]
  (fn [ident]
    (read-tree ks env ident)))