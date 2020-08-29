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
  ([m slash-id-required? id-attribute]
   [map? boolean? (? keyword?) => map?]
   (let [id-attribute (or id-attribute (id-attribute-f m))]
     (when slash-id-required?
       ;; TODO
       ;; We want to be alterable by config but have not yet done. See defaults.edn
       ;; This slash-id-required? always true, and we can get rid of when use RAD meta data to find equiv of /id
       (assert id-attribute
               ["Every value stored in the DB must have an /id attribute (current implementation limitation)"
                (keys m)]))
     (when id-attribute
       {id-attribute (get m id-attribute)})))
  ([m slash-id-required?]
   [map? boolean? => map?]
   (map->eql-result m slash-id-required? nil))
  ([m]
   [map? => map?]
   (map->eql-result m true nil)))

(defn map->ident [m]
  (let [[k v] (first (map->eql-result m))]
    [k v]))

;;
;; reference is an ident or a vector of idents, or a scalar (in which case not a reference)
;; just-id-map? means to return a map will only the id in it, so suitable for Pathom
;;
(defn idents->value [ks env just-id-map? reference]
  (assert (some? reference) ["nil reference/value s/never be kept in db" (kv-adaptor/instance-name-f ks env)])
  (let [recurse-f (partial idents->value ks env just-id-map?)]
    (cond
      (eql/ident? reference) (let [[table id] reference
                                   res (if just-id-map?
                                         {table id}
                                         (kv-adaptor/read* ks env reference))]
                               (assert (seq res) ["Not found a result" reference])
                               res)
      (vector? reference) (mapv recurse-f reference)
      :else reference)))

;;
;; denormalize-children? means we return as much of the tree as possible
;;
(>defn -read-outer
  [ks env denormalize-children? ident-or-m]
  [any? map? boolean? any? => map?]
  (assert (satisfies? kv-adaptor/KeyStore ks) ["Not a KeyStore" ks (keys (get env ::key-value/databases))])
  (let [ident (if (map? ident-or-m)
                (let [ident (into [] (first ident-or-m))]
                  (assert (= 1 (count ident-or-m))
                          ["Expecting to read an ident-like thing (a map with only one map-entry)" ident-or-m])
                  ident)
                (do
                  (assert (eql/ident? ident-or-m) ["Not an ident" ident-or-m])
                  ident-or-m))
        entity (kv-adaptor/read* ks env ident)]
    (assert entity ["Could not find entity from ident" ident (kv-adaptor/instance-name-f ks env)])
    (into {}
          (map (fn [[k v]]
                 (if (nil? v)
                   (do
                     (log/warn "nil value in database for attribute" k)
                     [k v])
                   [k (idents->value ks env (not denormalize-children?) v)])))
          entity)))

(defn read-tree [ks env ident-or-m]
  (-read-outer ks env true ident-or-m))

(defn read-compact [ks env ident-or-m]
  (-read-outer ks env false ident-or-m))