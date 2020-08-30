(ns com.fulcrologic.rad.database-adapters.key-value.read
  (:require
    [edn-query-language.core :as eql]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.rad.attributes :as attr]
    [taoensso.timbre :as log]))

(defn unwrap-id
  "Generate an id. You need to pass a `suggested-id` as a UUID or a tempid. If it is a tempid and the ID column is a UUID, then
  the UUID *from* the tempid will be used."
  [{::attr/keys [key->attribute] :as env} k suggested-id]
  (let [{::attr/keys [type]} (key->attribute k)]
    (cond
      (= :uuid type) (cond
                       (tempid/tempid? suggested-id) (:id suggested-id)
                       (uuid? suggested-id) suggested-id
                       :else (throw (ex-info "Only unwrapping of tempid/uuid is supported" {:id suggested-id})))
      :otherwise (throw (ex-info "Cannot generate an ID for non-uuid ID attribute" {:attribute k})))))

(defn slash-id-keyword? [kw]
  (re-matches #".*/id$" (str kw)))

(defn id-attribute-f [m]
  (when (map? m)
    (let [m-keys (->> m keys vec (remove #{:db/id}))
          id-attributes (filter slash-id-keyword? m-keys)]
      (when (= 1 (count id-attributes))
        (-> id-attributes first keyword)))))

(def slash-id-map? id-attribute-f)

(defn slash-id-ident? [x]
  (and (eql/ident? x)
       (-> x first slash-id-keyword?)))

(>defn map->eql-result
  ([m id-attribute]
   [map? (? keyword?) => map?]
   (let [id-attribute (or id-attribute (id-attribute-f m))]
     (when (nil? id-attribute)
       ;; We want to be /id by RAD config but have not yet done.
       (throw (ex-info "Every value stored in the DB must have an /id attribute (current implementation limitation)"
                       {:keys (keys m)})))
     {id-attribute (get m id-attribute)}))
  ([m]
   [map? => map?]
   (map->eql-result m nil)))

(defn map->ident [m]
  (let [[k v] (first (map->eql-result m))]
    [k v]))

;;
;; reference is an ident or a vector of idents, or a scalar (in which case not a reference)
;; just-id-map? means to return a map will only the id in it, so suitable for Pathom
;;
(>defn idents->value [ks env just-id-map? reference]
  [::kv-adaptor/key-store map? boolean? any? => any?]
  (let [recurse-f (partial idents->value ks env just-id-map?)]
    (cond
      (eql/ident? reference) (let [[table id] reference]
                               (if just-id-map?
                                 {table id}
                                 (kv-adaptor/read* ks env reference)))
      (vector? reference) (mapv recurse-f reference)
      :else reference)))

;;
;; denormalize-children? means we return as much of the tree as possible
;;
(defn -read-outer
  [ks env denormalize-children? ident-or-m]
  (let [ident (if (map? ident-or-m)
                (into [] (first ident-or-m))
                ident-or-m)
        entity (kv-adaptor/read* ks env ident)]
    (when entity
      (into {}
            (map (fn [[k v]]
                   (if (nil? v)
                     (do
                       (log/warn "nil value in database for attribute" k)
                       [k v])
                     [k (idents->value ks env (not denormalize-children?) v)])))
            entity))))

(>defn read-tree [ks env ident-or-m]
  [::kv-adaptor/key-store map? ::kv-adaptor/ident-or-map => (? map?)]
  (-read-outer ks env true ident-or-m))

(>defn read-compact [ks env ident-or-m]
  [::kv-adaptor/key-store map? ::kv-adaptor/ident-or-map => (? map?)]
  (-read-outer ks env false ident-or-m))