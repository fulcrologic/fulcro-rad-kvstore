(ns com.fulcrologic.rad.database-adapters.key-value.write
  "All entry points for writing to a ::key-value/KeyStore that are outside the protocol itself. `::write-tree` and
  `::remove-table-rows` are the ones to be familiar with"
  (:refer-clojure :exclude [flatten])
  (:require [edn-query-language.core :as eql]
            [com.fulcrologic.guardrails.core :refer [>defn => ?]]
            [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
            [com.fulcrologic.rad.database-adapters.key-value :as key-value]
            [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
            [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
            [clojure.walk :as walk]
            [taoensso.timbre :as log]))

;;
;; TODO (This is a 'done' but leaving b/c questions answered, please wipe after read!)
;; T
;; Assertions give a pretty bad experience IMO, because sometimes you get the assertion wrong and I'd rather is not
;; crash.
;; Why is `value` even names here when the function does not use it?
;;
;; C
;; Assertions are supposed to be development only?? So it shouldn't matter so much if you get the assertion wrong.
;; However (doing some research) it doesn't seem clear (or widely used) how to turn them off! Thus the spec version of
;; assert should be preferred.
;;
;; An 'always on' assert would be a `throw`. I've now changed a few assertions to throws. There are no more asserts
;; in this code.
;;
;; `value` doesn't need to be there. The signatures of these two functions just need to be the same because of how
;; they are used. I do care that the types are correct, even though they are selectively ignored. They are
;; being used to compose the seeded data. `ident-of` and `value-of` are interchangeable. So the requirement is that
;; they have the same signature.
;;

(>defn ident-of
  "Used when composing data to be stored. When a join is a reference (this function returns an ident reference) you
  are indicating that elsewhere the referred to entity is being included in its entirety. No need to repeat information.
  It is expected that the use of `ident-of` is interchanged with the use of `value-of` as the data composition changes.
  See com.example.components.seeded-connection/seed!"
  [[table id value]]
  [::key-value/table-id-entity => ::key-value/ident]
  [table id])

(>defn value-of
  "Used when composing data to be stored. Returns the value, something you only need to include once when composing the
  tree data structures that `::kv-write/write-tree` knows how to store.
  It is expected that the use of `value-of` is interchanged with the use of `ident-of` as the data composition changes.
  See com.example.components.seeded-connection/seed!"
  [[table id value]]
  [::key-value/table-id-entity => map?]
  value)

(defn id-attribute-f
  "Obtains the /id attribute from an entity"
  [m]
  [any? => (? ::key-value/id-keyword)]
  (when (map? m)
    (let [id-attributes (filter key-value/id-keyword? (keys m))
          attribute (first id-attributes)]
      (when (> (count id-attributes) 1)
        (log/error "More than one candidate id attribute, picked" attribute))
      attribute)))

(def id-entity?
  "Does the entity have a proper /id attribute?"
  id-attribute-f)

(defn id-ident?
  "Is it an ident of an entity?"
  [x]
  (and (eql/ident? x)
       (-> x first key-value/id-keyword?)))

(defn- to-one-join?
  "This map-entry has a value that indicates it is a reference to one other"
  [x]
  (when (map-entry? x)
    (let [[k v] x]
      ((some-fn id-ident? id-entity?) v))))

(defn- to-many-join?
  "This map-entry has a value that indicates it is a reference to many others (as long as have already counted out
  that could be a to-one-join?)"
  [x]
  (when (map-entry? x)
    (let [[k v] x]
      (vector? v))))

(defn- parent-keyword? [k]
  (and (qualified-keyword? k)
       (= (namespace k) "parent-join")))

(defn- parent-join? [x]
  (when (map-entry? x)
    (let [[k v] x]
      (parent-keyword? k))))

(defn gen-protected-id!
  "Generate a special id that will only exist as part of flattening. Use to insert an ident when need to"
  []
  (keyword "parent-join" (str (gensym))))

(defn- dissoc-parent-joins [m]
  (reduce-kv
    (fn [m k v]
      (if (parent-keyword? k)
        m
        (assoc m k v)))
    {}
    m))

(>defn entity->eql-result
  "Remove all attribute keys from the entity except for the identifying one. Thus returns a map with only one
  map-entry, the /id one, which is what Pathom wants to return from a resolver"
  ([m id-attribute]
   [map? (? qualified-keyword?) => map?]
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
  [map? => ::key-value/ident]
  (let [[k v] (first (entity->eql-result m))]
    [k v]))

(defn ident-ify
  "Turn a join into an ident. Are losing information. Makes sense after flattening."
  [[attrib v]]
  (cond
    (map? v) [attrib (entity->ident v)]
    (and (vector? v) (-> v first map?)) [attrib (mapv #(entity->ident %) v)]
    (eql/ident? v) [attrib v]
    (and (vector? v) (-> v first eql/ident?)) [attrib v]
    :else [attrib v]))

(defn first-parse-flatten
  "Produces this data structure:

  ([[:account/id #uuid \"ffffffff-ffff-ffff-ffff-000000000100\"]
    {:account/role :account.role/user,
     :account/active? true,
     :password/hashed-value \"dig3U/JleCoGsKJ9/ip88KGzZKL82zZLIdgSySa+IK/Q/V+hYPjGw/9V9iw4ZJhkm7MbvKM5R2ORORph4ndIWA==\",
     :account/email \"tony@example.com\",
     :account/addresses [[:address/id #uuid \"ffffffff-ffff-ffff-ffff-000000000001\"]],
     :account/primary-address #:address{:id #uuid \"ffffffff-ffff-ffff-ffff-000000000300\", :street \"222 Other\", :city \"Sacramento\", :state :address.state/CA, :zip \"99999\"},
     :account/name \"Tony\"}]
   [:address/id #uuid \"ffffffff-ffff-ffff-ffff-000000000001\"]
   [[:address/id #uuid \"ffffffff-ffff-ffff-ffff-000000000300\"]
    #:address{:id #uuid \"ffffffff-ffff-ffff-ffff-000000000300\", :street \"222 Other\", :city \"Sacramento\", :state :address.state/CA, :zip \"99999\"}])

  The first element of this structure is an ident/map pair. Then an ident on its own. Then another ident/map pair
  i.e. [ident {}]. Produces entries such that each entry is an ident if that's all that's available, but [ident {}]
  where the entity has been found as well (in input).
  Usually the ident entries can be thrown away, assuming they have been put in state elsewhere (so assuming the ref is good).
  When saving from these flattened records we only have to care about the entry and can `ident-ify` all the joins.
  Address of 300 in RAD Demo required this. Without this kind of thinking the entity would have been lost, leaving a
  dangling ident pointing to nowhere.
  In this function we need to keep any maps so we can recurse through them. Once flattened, and on a second parse,
  we can turn all maps into indents as they should be stored in the `KeyValue` database"
  [x]
  (cond
    (parent-join? x) (let [[k v] x]
                       [v])
    (to-one-join? x) (let [[k v] x]
                       (if (eql/ident? v)
                         [v]
                         (mapcat first-parse-flatten (assoc v (gen-protected-id!) [(entity->ident v) v]))))
    (to-many-join? x) (let [[k v] x]
                        (mapcat first-parse-flatten v))
    (map-entry? x) []
    (map? x) (mapcat first-parse-flatten (assoc x (gen-protected-id!) [(entity->ident x) x]))
    (id-ident? x) [x]))

(>defn flatten
  "To comprehend and process recursive information we flatten it, but without losing any information. There is no
  check done on the integrity of the data. But when putting it together the functions `ident-of` and `value-of` are
  supposed to help. Just make sure that for every `ident-of` there is at least one `value-of` of the same entity"
  [m]
  [map? => ::key-value/pairs-of-ident-map]
  (->> (first-parse-flatten (assoc m (gen-protected-id!) [(entity->ident m) m]))
       ;; ignore the [ident] entries, assuming they are already in state
       (remove eql/ident?)
       (map (fn [[ident m]]
              [ident (->> m
                          (map ident-ify)
                          (into {})
                          dissoc-parent-joins)]))
       distinct))

(>defn write-tree
  "Writing will work whether given denormalized or normalized. Use this function to seed/import large amounts of
  data. As long as the input is coherent all the references should be respected. See
  `com.example.components.seeded-connection/seed!` for example usage."
  [ks env m]
  [::kv-adaptor/key-store map? map? => any?]
  (let [entries (flatten m)]
    (kv-adaptor/write* ks env entries)))

(>defn remove-table-rows!
  "Given a table find out all its rows and remove them"
  [ks env table]
  [::kv-adaptor/key-store map? ::key-value/id-keyword => any?]
  (let [idents (kv-adaptor/read-table ks env table)]
    (doseq [ident idents]
      (kv-adaptor/remove1 ks env ident))))

(def before-after? (every-pred map? #(= 2 (count %)) #(contains? % :before) #(contains? % :after)))

(defn after-only
  "If given a [:before :after] map then ignore that aspect, just returning the `:after`.
  For the moment we just get rid of the before and after stuff rather than using it properly.
  Our updates will always succeed when sometimes they should not - where someone else got in before us."
  [v]
  (if (before-after? v)
    (:after v)
    v))

(defn- handle-before-after-1 [_ m]
  (->> m
       (map (fn [[attrib attrib-v]]
              [attrib (-> attrib-v
                          after-only)]))
       (into {})))

;;
;; Let's only use this when have an environment option for it.
;; do-not-store-nil? true
;; `nil` is of course usually legitimate to put in into a DB.
;; Could be handled earlier, at the form layer.
;;
(defn- handle-before-after-2 [new-entity? m]
  (into {}
        (keep (fn [[attrib attrib-v]]
                (if (before-after? attrib-v)
                  (let [{:keys [before after]} attrib-v]
                    (when-not (and new-entity? #_(= before after) (nil? after))
                      [attrib after]))
                  [attrib attrib-v])))
        m))

(>defn write-delta
  "What a delta looks like (only one map-entry here):

    {[:account/id #uuid \"ffffffff-ffff-ffff-ffff-000000000100\"]
     {:account/active? {:before true, :after false}}}

  Unwrapping means no need for any lookup tables, can just generate :tempids map for return.
  Theoretically at return time just go through the delta grab all ids that are Fulcro tempids.
  Then generate a table using tempid->uuid.
  However tempid handling already being done outside this function, so just returning {}.
  For writing to our db we can just unwrap tempids, seen here in postwalk"
  [ks env delta]
  [::kv-adaptor/key-store map? map? => map?]
  (kv-adaptor/write* ks env
                     (->> delta
                          (map (fn [[[table id] m]]
                                 (when (string? id)
                                   (throw (ex-info "String id means need to support string tempids. (Only Fulcro tempids currently supported)" {:id id})))
                                 (let [handle-before-after (partial handle-before-after-1 (tempid/tempid? id))]
                                   [[table id] (-> m
                                                   handle-before-after
                                                   (assoc table id))])))
                          (walk/postwalk
                            (fn [x] (if (tempid/tempid? x)
                                      (:id x)
                                      x)))
                          (into {})))
  ;; :tempids handled by caller
  {})