(ns com.fulcrologic.rad.database-adapters.key-value.write
  (:refer-clojure :exclude [flatten])
  (:require [edn-query-language.core :as eql]
            [com.fulcrologic.rad.database-adapters.key-value :as key-value]
            [com.fulcrologic.guardrails.core :refer [>defn => ?]]
            [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
            [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
            [com.fulcrologic.rad.database-adapters.key-value.read :as key-value-read :refer [slash-id-keyword?]]
            [com.fulcrologic.rad.attributes :as attr]
            [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
            [clojure.walk :as walk]
            [clojure.spec.alpha :as s]))

(>defn ident-of [[table id _]]
  [(s/tuple slash-id-keyword? uuid? any?) => eql/ident?]
  [table id])

(>defn value-of [[table id value]]
  [(s/tuple slash-id-keyword? uuid? map?) => map?]
  value)

(defn to-one-join? [x]
  (when (map-entry? x)
    (let [[k v] x]
      ((some-fn key-value-read/slash-id-ident? key-value-read/slash-id-map?) v))))

(defn to-many-join? [x]
  (when (map-entry? x)
    (let [[k v] x]
      (vector? v))))

(defn parent-keyword? [k]
  (and (keyword? k)
       (= (namespace k) "parent-join")))

(defn parent-join? [x]
  (when (map-entry? x)
    (let [[k v] x]
      (parent-keyword? k))))

(defn gen-protected-id! []
  (keyword "parent-join" (str (gensym))))

(defn dissoc-parent-joins [m]
  (reduce-kv
    (fn [m k v]
      (if (parent-keyword? k)
        m
        (assoc m k v)))
    {}
    m))

(defn ident-ify [[attrib v]]
  (cond
    (map? v) [attrib (key-value-read/map->ident v)]
    (and (vector? v) (-> v first map?)) [attrib (mapv #(key-value-read/map->ident %) v)]
    (eql/ident? v) [attrib v]
    (and (vector? v) (-> v first eql/ident?)) [attrib v]
    :else [attrib v]))

;;
;; Produces this structure:
;;
; ([[:account/id #uuid "ffffffff-ffff-ffff-ffff-000000000100"]
;  {:account/role :account.role/user,
;   :account/active? true,
;   :password/hashed-value "dig3U/JleCoGsKJ9/ip88KGzZKL82zZLIdgSySa+IK/Q/V+hYPjGw/9V9iw4ZJhkm7MbvKM5R2ORORph4ndIWA==",
;   :account/email "tony@example.com",
;   :account/addresses [[:address/id #uuid "ffffffff-ffff-ffff-ffff-000000000001"]],
;   :account/primary-address #:address{:id #uuid "ffffffff-ffff-ffff-ffff-000000000300", :street "222 Other", :city "Sacramento", :state :address.state/CA, :zip "99999"},
;   :account/name "Tony"}]
; [:address/id #uuid "ffffffff-ffff-ffff-ffff-000000000001"]
; [[:address/id #uuid "ffffffff-ffff-ffff-ffff-000000000300"]
;  #:address{:id #uuid "ffffffff-ffff-ffff-ffff-000000000300", :street "222 Other", :city "Sacramento", :state :address.state/CA, :zip "99999"}])
;;
;; Produces entries such that each entry:
;; Is an ident if that's all that's available, but [ident {}] where found the entity as well (in what user is writing)
;; We ignore the ident entries, assuming they have been put in state elsewhere (so assuming the ref is good).
;; When saving flattened records we only have to care about the entry and can map->ident all the joins
;; Address of 300 in RAD Demo required this. Without this kind of thinking the entity would have been lost, leaving an
;; dangling ident pointing to it.
;; In this function we need to keep any maps so we can move through them. Once flattened, and on a second parse,
;; we can turn all maps into indents as they should be stored.
;;
(defn first-parse-flatten [x]
  (cond
    (parent-join? x) (let [[k v] x]
                       [v])
    (to-one-join? x) (let [[k v] x]
                       (if (eql/ident? v)
                         [v]
                         (mapcat first-parse-flatten (assoc v (gen-protected-id!) [(key-value-read/map->ident v) v]))))
    (to-many-join? x) (let [[k v] x]
                        (mapcat first-parse-flatten v))
    (map-entry? x) []
    (map? x) (mapcat first-parse-flatten (assoc x (gen-protected-id!) [(key-value-read/map->ident x) x]))
    (key-value-read/slash-id-ident? x) [x]))

(defn flatten [m]
  (->> (first-parse-flatten (assoc m (gen-protected-id!) [(key-value-read/map->ident m) m]))
       ;; ignore the [ident] entries, assuming they are already in state
       (remove eql/ident?)
       (map (fn [[ident m]]
              [ident (->> m
                          (map ident-ify)
                          (into {})
                          dissoc-parent-joins)]))
       distinct))

;;
;; Writing will work whether given denormalized or normalized
;;
(>defn write-tree
  [ks env ident m]
  [any? map? eql/ident? map? => any?]
  (assert (satisfies? kv-adaptor/KeyStore ks) ["ks is not a KeyStore" ks (keys (get env ::key-value/databases))])
  (assert (eql/ident? ident) ["Every key must be an ident" ident (type ident)])
  (assert (map? m) ["Everything stored at a key must be a map" m (type m)])
  (let [entries (flatten m)]
    (kv-adaptor/write* ks env entries)))

(>defn remove-table-rows
  [ks env table]
  [any? map? keyword? => any?]
  (assert (satisfies? kv-adaptor/KeyStore ks) ["ks is not a KeyStore" ks (keys (get env ::key-value/databases))])
  (let [idents (->> (kv-adaptor/read* ks env table)
                    (map (fn [m] [table (get m table)])))]
    (doseq [ident idents]
      (kv-adaptor/remove1 ks env ident))))

(def before-after? (every-pred map? #(= 2 (count %)) #(contains? % :before) #(contains? % :after)))

;;
;; For the moment we just get rid of the before and after stuff rather than using it properly.
;; Our updates will always succeed when sometimes they should not - where someone else got in before us.
;;
(defn after-only [v]
  (if (before-after? v)
    (:after v)
    v))

;;
;; What a delta looks like (only one map-entry here):
;; {[:account/id #uuid "ffffffff-ffff-ffff-ffff-000000000100"]
;;  {:account/active? {:before true, :after false}}}
;;
;; Unwrapping means no need for any lookup tables, can just generate :tempids map for return.
;; Theoretically at return time just go thru the delta grab all ids are Fulcro tempids. Then gen a table using tempid->uuid.
;; However tempid handling already being done outside this function, so just returning {}.
;; For writing to our db we can just unwrap tempids, seen here in postwalk
;;
(>defn write-delta
  [ks {::attr/keys [key->attribute] :as env} delta]
  [any? map? map? => map?]
  (assert key->attribute ["Don't have key->attribute in env" (keys env)])
  (kv-adaptor/write* ks (assoc env :debug? true)
                     (->> delta
                          (walk/postwalk
                            (fn [x] (if (tempid/tempid? x)
                                      (:id x)
                                      x)))
                          (map (fn [[[table id] m]]
                                 (assert (-> id string? not) ["String id means need to support string tempids. (Only Fulcro tempids supported)" id])
                                 [[table id] (-> m
                                                 (#(->> %
                                                        (map (fn [[attrib attrib-v]]
                                                               [attrib (-> attrib-v
                                                                           after-only)]))
                                                        (into {})))
                                                 (assoc table id))]))
                          (into {})))
  ;; :tempids handled by caller
  {})