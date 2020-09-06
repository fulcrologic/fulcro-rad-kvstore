(ns com.fulcrologic.rad.database-adapters.key-value.pathom-k
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [edn-query-language.core :as eql]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [konserve.core :as k]
    [clojure.core.async :as async :refer [<!! chan go go-loop]]
    [taoensso.timbre :as log]))

(>defn idents->value-hof
       "reference is an ident or a vector of idents, or a scalar (in which case not a reference). Does not do any database
       reading, just changes [table id] to {table id}"
       [env]
       [map? => fn?]
       (fn [reference]
         (cond
           (eql/ident? reference) (let [[table id] reference]
                                    {table id})
           (vector? reference) (let [recurse-f (idents->value-hof env)]
                                 (mapv recurse-f reference))
           :else reference)))

(defn read-compact
  "Reads once from the database using `::kv-adaptor/read1` then transforms the ident joins into /id only (ident-like) maps"
  [ks env ident]
  (let [entity (<!! (k/get-in ks ident))]
    (when entity
      (into {}
            (map (fn [[k v]]
                   (if (nil? v)
                     (do
                       (log/warn "`::kv-pathom/read-compact` nil value in database for attribute" k)
                       [k v])
                     [k ((idents->value-hof env) v)])))
            entity))))

