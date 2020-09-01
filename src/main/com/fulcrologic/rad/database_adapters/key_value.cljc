(ns com.fulcrologic.rad.database-adapters.key-value
  (:require
    [clojure.spec.alpha :as s]))

(def id-keyword? #(and (namespace %) (= "id" (name %))))

(s/def ::id-keyword id-keyword?)

(s/def ::ident-like-map (every-pred map?
                                    #(= 1 (count %))
                                    #(-> % first key id-keyword?)
                                    #(-> % first val uuid?)))

(s/def ::ident (s/tuple ::id-keyword uuid?))

(s/def ::idents (s/coll-of ::ident :kind vector?))

(s/def ::pair (s/tuple ::ident map?))

(s/def ::pairs-of-ident-map (s/coll-of ::pair))

(s/def ::tables (s/coll-of ::id-keyword :kind vector?))

(s/def ::table-id-entity (s/tuple ::id-keyword uuid? map?))

(s/def ::ident-s-or-table (s/or :ident ::ident
                                :idents ::idents
                                :table ::id-keyword))

