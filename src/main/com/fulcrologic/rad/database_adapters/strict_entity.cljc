(ns com.fulcrologic.rad.database-adapters.strict-entity
  "Entities that have a strict definition of their ids (/id for the identifying attribute and uuid? for its value)
  are strict entities"
  (:require
    [edn-query-language.core :as eql]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [taoensso.timbre :as log]
    [clojure.spec.alpha :as s]))

(def id-keyword? #(and (namespace %) (= "id" (name %))))

(s/def ::id-keyword id-keyword?)

(s/def ::table id-keyword?)

(s/def ::id uuid?)

(s/def ::ident (s/tuple ::table ::id))

;;
;; To be checking that it is a strict entity every time would be too much
;;
(s/def ::entity map?)

(defn id-attribute-f
  "Obtains the /id attribute from an entity"
  [m]
  [any? => (? ::id-keyword)]
  (when (map? m)
    (let [id-attributes (filter id-keyword? (keys m))
          attribute (first id-attributes)]
      (when (> (count id-attributes) 1)
        (log/error "More than one candidate id attribute, picked" attribute))
      attribute)))

(def id-entity?
  "Does the entity have a proper /id attribute?"
  id-attribute-f)

(>defn entity->eql-result
  "Remove all attribute keys from the entity except for the identifying one. Thus returns a map with only one
  map-entry, the /id one, which is what Pathom wants to return from a resolver"
  ([m id-attribute]
   [map? (? qualified-keyword?) => map?]
   (let [id-attribute (or id-attribute (id-attribute-f m))]
     (if (nil? id-attribute)
       ;; We want to be /id by RAD config but have not yet done.
       (do
         (log/error "Every value/map stored in the Key Value DB must have an /id attribute (current implementation limitation), else provide one to `::kv-write/entity->eql-result`")
         {})
       {id-attribute (get m id-attribute)})))
  ([m]
   [map? => map?]
   (entity->eql-result m nil)))

(>defn entity->ident
  "Given an entity, return the ident for that entity"
  [m]
  [map? => ::ident]
  (let [[k v] (first (entity->eql-result m))]
    [k v]))

(defn ident-ify
  "Turn a join into an ident (or vector of idents). Are losing information. Makes sense after flattening the tree"
  [[attrib v]]
  (cond
    (map? v) [attrib (entity->ident v)]
    (and (vector? v) (-> v first map?)) [attrib (mapv #(entity->ident %) v)]
    (eql/ident? v) [attrib v]
    (and (vector? v) (-> v first eql/ident?)) [attrib v]
    :else [attrib v]))

