(ns com.fulcrologic.rad.database-adapters.key-value
  (:require [clojure.spec.alpha :as s]
            [edn-query-language.core :as eql]))

(s/def ::ident-like-map (every-pred map? #(= 1 (count %))))

(s/def ::idents (s/coll-of eql/ident? :kind vector?))

(s/def ::pairs-of-ident-map any?)

(s/def ::slash-id-keyword #(and (namespace %) (= "id" (name %))))

(s/def ::tables (s/coll-of keyword? :kind vector?))

