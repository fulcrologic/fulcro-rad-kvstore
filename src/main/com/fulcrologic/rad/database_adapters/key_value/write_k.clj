(ns com.fulcrologic.rad.database-adapters.key-value.write-k
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [konserve.core :as k]
    [clojure.core.async :as async :refer [<!! chan go go-loop]]
    [general.dev :as dev]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [clojure.walk :as walk]))

(>defn write-tree
  "Writing will work whether given denormalized or normalized. Use this function to seed/import large amounts of
  data. As long as the input is coherent all the references should be respected. See
  `com.example.components.seeded-connection/seed!` for example usage."
  [ks env m]
  [::kv-adaptor/key-store map? map? => any?]
  (let [store (kv-adaptor/store ks)
        entries (kv-write/flatten m)]
    (go-loop [entries entries]
      (when-let [[ident m] (first entries)]
        (k/assoc-in store ident m)
        (recur (rest entries))))))

(>defn remove-table-rows!
  "Given a table find out all its rows and remove them"
  [ks env table]
  [::kv-adaptor/key-store map? ::key-value/table => any?]
  (go (k/dissoc (kv-adaptor/store ks) table)))

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
  (let [pairs-of-ident-map (->> delta
                                (map (fn [[[table id] m]]
                                       (when (string? id)
                                         (throw (ex-info "String id means need to support string tempids. (Only Fulcro tempids currently supported)" {:id id})))
                                       (let [handle-before-after (if (:key-value/dont-store-nils? (kv-adaptor/options ks))
                                                                   (kv-write/expand-to-after-no-nils-hof (tempid/tempid? id))
                                                                   kv-write/expand-to-after)]
                                         [[table id] (-> m
                                                         handle-before-after
                                                         (assoc table id))])))
                                (walk/postwalk
                                  (fn [x] (if (tempid/tempid? x)
                                            (:id x)
                                            x)))
                                (into {}))
        pairs (cond
                ((every-pred seq (complement map?)) pairs-of-ident-map) pairs-of-ident-map
                (map? pairs-of-ident-map) (into [] pairs-of-ident-map))]
    (go-loop [[pair & more] pairs]
      (when-let [[ident m] (if (map? pair)
                             (first pair)
                             pair)]
        (k/update-in ident merge m)
        (recur more))))
  ;; :tempids handled by caller
  {})


