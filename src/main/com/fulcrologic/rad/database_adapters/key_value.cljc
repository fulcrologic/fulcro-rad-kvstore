(ns com.fulcrologic.rad.database-adapters.key-value
  (:require
    [clojure.spec.alpha :as s]))

(def slash-id-keyword? #(and (namespace %) (= "id" (name %))))

(s/def ::slash-id-keyword slash-id-keyword?)

(s/def ::ident-like-map (every-pred map?
                                    #(= 1 (count %))
                                    #(-> % first key slash-id-keyword?)
                                    #(-> % first val uuid?)))

(s/def ::ident (s/tuple ::slash-id-keyword uuid?))

(s/def ::idents (s/coll-of ::ident :kind vector?))

(s/def ::pair (s/tuple ::ident map?))

(s/def ::pairs-of-ident-map (s/coll-of ::pair))

(s/def ::tables (s/coll-of keyword? :kind vector?))

(s/def ::table-id-entity (s/tuple ::slash-id-keyword uuid? map?))

