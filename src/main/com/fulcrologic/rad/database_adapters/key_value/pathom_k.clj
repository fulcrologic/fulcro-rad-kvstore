(ns com.fulcrologic.rad.database-adapters.key-value.pathom-k
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [edn-query-language.core :as eql]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [konserve.core :as k]
    [clojure.core.async :as async :refer [<!! <! chan go go-loop]]
    [taoensso.timbre :as log]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]))

(>defn idents->value-hof
  "reference is an ident or a vector of idents, or a scalar (in which case not a reference). Does not do any database
  reading, just changes [table id] to {table id}"
  [env]
  [map? => fn?]
  (fn [reference]
    (cond
      (eql/ident? reference) (let [[table id] reference]
                               {table id})
      (vector? reference) (let [recurse-f (idents->value-hof env)]
                            (mapv recurse-f reference))
      :else reference)))

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

(defn entity-query
  "Performs the query of the Key Value database. Uses the id-attribute that needs to be resolved and the input to the
  resolver which will contain that id/s the need to be queried for"
  [{::key-value/keys [id-attribute]
    ::attr/keys      [schema attributes]
    :as              env}
   input
   context]
  (let [{::attr/keys [qualified-key]} id-attribute
        one? (not (sequential? input))
        [db kind] context]
    (let [ids (if one?
                [(get input qualified-key)]
                (into [] (keep #(get % qualified-key)) input))
          idents (mapv (fn [id]
                         [qualified-key (unwrap-id env qualified-key id)])
                       ids)]
      (let [result (mapv (fn [ident]
                           (<!!
                             (go
                               (let [entity (<! (k/get-in (kv-adaptor/store db) ident))]
                                 (when entity
                                   (into {}
                                         (map (fn [[k v]]
                                                (if (nil? v)
                                                  (do
                                                    (log/warn "`::kv-pathom/read-compact` nil value in database for attribute" k)
                                                    [k v])
                                                  [k ((idents->value-hof env) v)])))
                                         entity))))))
                         idents)]
        (if one?
          (first result)
          result)))))

