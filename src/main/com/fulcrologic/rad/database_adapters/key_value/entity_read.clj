(ns com.fulcrologic.rad.database-adapters.key-value.entity-read
  (:require
    [edn-query-language.core :as eql]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [taoensso.timbre :as log]))

(>defn slash-id-keyword?
  "It it a fully qualified keyword with \"id\" for its name?"
  [kw]
  [keyword? => boolean?]
  (and (namespace kw) (= "id" (name kw))))

(defn id-attribute-f
  "Obtains the /id attribute from an entity"
  [m]
  [any? => (? keyword?)]
  (when (map? m)
    (let [id-attributes (filter slash-id-keyword? (keys m))
          attribute (first id-attributes)]
      (when (> (count id-attributes) 1)
        (log/error "More than one candidate id attribute, picked" attribute))
      attribute)))

(def slash-id-entity?
  "Does the entity have a proper /id attribute?"
  id-attribute-f)

(defn slash-id-ident?
  "Is it an ident of an entity?"
  [x]
  (and (eql/ident? x)
       (-> x first slash-id-keyword?)))

(>defn entity->eql-result
  "Remove all attribute keys from the entity except for the identifying one. Thus returns a map with only one
  map-entry, the /id one, which is what Pathom wants to return from a resolver"
  ([m id-attribute]
   [map? (? keyword?) => map?]
   (let [id-attribute (or id-attribute (id-attribute-f m))]
     (when (nil? id-attribute)
       ;; We want to be /id by RAD config but have not yet done.
       (throw (ex-info "Every value/map stored in the Key Value DB must have an /id attribute (current implementation limitation)"
                       {:keys (keys m)})))
     {id-attribute (get m id-attribute)}))
  ([m]
   [map? => map?]
   (entity->eql-result m nil)))

(>defn entity->ident
  "Given an entity, return the ident for that entity"
  [m]
  [map? => eql/ident?]
  (let [[k v] (first (entity->eql-result m))]
    [k v]))

(defn- idents->value-hof
  "reference is an ident or a vector of idents, or a scalar (in which case not a reference)"
  [ks env]
  (fn [reference]
    (cond
      (eql/ident? reference) (case (kv-adaptor/cardinality reference)
                               :ident (kv-adaptor/read1 ks env reference)
                               :idents (kv-adaptor/read* ks env reference))
      (vector? reference) (let [recurse-f (idents->value-hof ks env)]
                            (mapv recurse-f reference))
      :else reference)))

(defn idents->value
  "Expands the reference/s into map/s, by reading from the database when just-id-map? is false. Does not need to
  read from the database when just-id-map? is true"
  [ks env reference]
  [::kv-adaptor/key-store map? any? => any?]
  ((idents->value-hof ks env) reference))

(>defn read-tree
  "Given the starting point of an ident or an entity will recursively keep reading the joins,
  returning the expanded tree"
  [ks env m]
  [::kv-adaptor/key-store map? ::key-value/ident-like-map => (? map?)]
  (let [ident (into [] (first m))
        entity (kv-adaptor/read1 ks env ident)]
    (when entity
      (into {}
            (map (fn [[k v]]
                   (if (nil? v)
                     (do
                       (log/warn "nil value in database for attribute" k)
                       [k v])
                     [k (idents->value ks env v)])))
            entity))))

(>defn read-tree-hof
  "A higher order function (hof) used to return a function that recursively returns as much of the tree as possible,
  when given any ident-like map. Typical usage:
  (let [read-tree (kv-entity-read/read-tree-hof db env)]
    (->> (kv-adaptor/read-table db env :account/id)
         (map read-tree)
         (filter :account/active?)
         (mapv #(select-keys % [:account/id]))))
 "
  [ks env]
  [::kv-adaptor/key-store map? => fn?]
  (fn [m]
    (read-tree ks env m)))