(ns com.fulcrologic.rad.database-adapters.key-value.pathom
  "Entry points for Pathom"
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.duplicates :as dups]
    [com.fulcrologic.rad.database-adapters.key-value-options :as kvo]
    [clojure.pprint :refer [pprint]]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.form :as form]
    [com.wsscode.pathom.core :as p]
    [taoensso.encore :as enc]
    [com.wsscode.pathom.connect :as pc]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.rad.authorization :as auth]
    [edn-query-language.core :as eql]
    [clojure.spec.alpha :as s]
    [konserve.core :as k]
    #?(:clj [clojure.core.async :as async :refer [<!!]])
    #?(:cljs [cljs.core.async :as async :refer [<! go]])
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
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

(defn tempids-in-delta
  "delta is key-ed by ident, so easy to find all the ids that are tempid-s"
  [delta]
  (into #{} (keep (fn [[table id :as ident]]
                    (when (tempid/tempid? id)
                      id))
                  (keys delta))))

(>defn delta->tempid-maps
  [env delta]
  [map? map? => map?]
  (dups/delta->tempid-maps tempids-in-delta unwrap-id env delta))

;;
;; TODO
;; Currently the atom is serving no purpose with only one connection.
;; Is only one connection fine?
;; If so kvo/connections -> ::key-value/connection and same with databases, or get rid of databases
;; altogether, as there is no difference between a connection and a database with KeyStore.
;; Multiple databases can exist in the config file, but don't need to be in pathom env.
;; However I suspect the answer is need to support multiple databases, and doesn't matter that connection is same as
;; a database - the goal is to have it work same as the Datomic database adapter - support multiple databases at
;; runtime.
;;

;;
;; Need kv-entry either kvo/databases or kvo/connections
;; Although no difference between them!
;; (This copying of Datomic code is leading to farcical results, hence above TODO)
;;
(>defn env->key-store
  "Find the keystore from the Pathom env, optionally given a schema and whether required from connections or databases"
  ([env schema kv-entry]
   [map? keyword? keyword? => ::key-value/key-store]
   (if-let [{::kv-key-store/keys [options] :as key-store}
            (cond
              (= kv-entry kvo/connections) (some-> (get-in env [kv-entry schema]))
              (= kv-entry kvo/databases) (some-> (get-in env [kv-entry schema]) deref))]
     (if-not (s/valid? ::key-value/key-store key-store)
       (throw (ex-info "Not a `::key-value/key-store`" {:key-store key-store
                                                        :env-keys  (keys env)}))
       key-store)
     (log/error (str "No database atom for schema: " schema))))
  ([env]
   [map? => ::key-value/key-store]
   (env->key-store env :production kvo/databases)))

(declare context)

;;
;; Surely save-form! is never going to be run in the browser? This function doesn't return a channel and we need to
;; use <!! in order to actually write.
;;
(defn save-form!
  "Do all of the possible operations for the given form delta (save to the Key Value database involved)"
  [env {::form/keys [delta] :as save-params}]
  (let [schemas (dups/schemas-for-delta env delta)
        result  (atom {:tempids {}})]
    (log/debug "Saving form across " schemas)
    (doseq [schema schemas
            :let [key-store (env->key-store env schema kvo/connections)
                  {:keys [tempid->string
                          tempid->generated-id]} (delta->tempid-maps env delta)]]
      (log/debug "Saving form delta\n" (with-out-str (pprint delta)))
      (log/debug "on schema" schema)
      (if key-store
        (try
          (kv-write/write-delta key-store env delta)
          (let [tempid->real-id (into {}
                                      (map (fn [tempid] [tempid (get tempid->generated-id tempid)]))
                                      (keys tempid->string))]
            ;; Datomic needs to read the world as moved forward by this transaction. No similar concept b/c we
            ;; are always at the most recent.
            ;(when database-atom
            ;  (reset! database-atom (d/db connection)))
            (swap! result update :tempids merge tempid->real-id))
          (catch #?(:clj Exception :cljs js/Object) e
            (log/error e "Transaction failed!")
            {}))
        (log/error "Unable to save form. Connection missing in env." (keys env))))
    @result))

(defn delete-entity!
  "Delete the given entity, if possible."
  [{::attr/keys [key->attribute] :as env} params]
  (enc/if-let [pk (ffirst params)
               id (get params pk)
               ident [pk id]
               {:keys [::attr/schema]} (key->attribute pk)
               {::kv-key-store/keys [store]} (-> env kvo/connections (get schema))]
              (do
                #?(:clj  (<!! (k/update store pk dissoc id))
                   :cljs (go (<! (k/update store pk dissoc id))))
                {})
              (log/warn "Key Value adapter failed to delete " params)))

(defn deep-merge
  "Merges nested maps without overwriting existing keys."
  [& xs]
  (if (every? map? xs)
    (apply merge-with deep-merge xs)
    (last xs)))

(defn wrap-save
  "Form save middleware to accomplish saves."
  ([]
   (fn [{::form/keys [params] :as pathom-env}]
     (let [save-result (save-form! pathom-env params)]
       save-result)))
  ([handler]
   (fn [{::form/keys [params] :as pathom-env}]
     (let [save-result    (save-form! pathom-env params)
           handler-result (handler pathom-env)]
       (deep-merge save-result handler-result)))))

(defn wrap-delete
  "Form delete middleware to accomplish deletes."
  ([handler]
   (fn [{::form/keys [params] :as pathom-env}]
     (let [local-result   (delete-entity! pathom-env params)
           handler-result (handler pathom-env)]
       (deep-merge handler-result local-result))))
  ([]
   (fn [{::form/keys [params] :as pathom-env}]
     (delete-entity! pathom-env params))))

(defn idents->value
  "reference is an ident or a vector of idents, or a scalar (in which case not a reference). Does not do any database
  reading, just changes [table id] to {table id}"
  [reference]
  (cond
    (eql/ident? reference) (let [[table id] reference]
                             {table id})
    (vector? reference) (let []
                          (mapv idents->value reference))
    :else reference))

(defn transform-entity
  "Transform so all the joins are no longer idents but ident-like entity maps"
  [entity]
  (into {}
        (map (fn [[k v]]
               (if (nil? v)
                 (do
                   (log/warn "nil value in database for attribute" k)
                   [k v])
                 [k (idents->value v)])))
        entity))

(>defn entity-query
  "Performs the query of the Key Value database. Uses the id-attribute that needs to be resolved and the input to the
  resolver which will contain the id/s that need to be queried for"
  [{::key-value/keys [id-attribute]
    :as              env}
   input
   {::kv-key-store/keys [ids->entities]}]
  [map? any? ::key-value/key-store => any?]
  (let [{::attr/keys [qualified-key]} id-attribute
        one? (not (sequential? input))]
    (let [ids (if one?
                [(get input qualified-key)]
                (into [] (keep #(get % qualified-key)) input))
          ids (map #(unwrap-id env qualified-key %) ids)]
      (let [entities (ids->entities qualified-key ids)
            result   (mapv transform-entity entities)]
        (if one?
          (first result)
          result)))))

(defn id-resolver
  "Generates a resolver from `id-attribute` to the `output-attributes`."
  [all-attributes
   {::attr/keys [qualified-key] :keys [::attr/schema ::key-value/wrap-resolve] :as id-attribute}
   output-attributes]
  [::attr/attributes ::attr/attribute ::attr/attributes => ::pc/resolver]
  (let [outputs          (attr/attributes->eql output-attributes)
        resolve-sym      (symbol
                           (str (namespace qualified-key))
                           (str (name qualified-key) "-resolver"))
        with-resolve-sym (fn [r]
                           (fn [env input]
                             (r (assoc env ::pc/sym resolve-sym) input)))]
    (log/info "Building ID resolver for" qualified-key "outputs" outputs)
    {::pc/sym     resolve-sym
     ::pc/input   #{qualified-key}
     ::pc/output  outputs
     ::pc/batch?  true
     ::pc/resolve (cond-> (fn [{::attr/keys [key->attribute] :as env} input]
                            (log/debug "In resolver:" qualified-key "inputs:" (vec input))
                            (let [key-store (env->key-store env schema kvo/databases)]
                              (->> (entity-query
                                     (assoc env ::key-value/id-attribute id-attribute)
                                     input
                                     key-store)
                                   (auth/redact env))))
                          wrap-resolve (wrap-resolve)
                          :always (with-resolve-sym))}))

(defn generate-resolvers [attributes schema]
  (dups/generate-resolvers id-resolver attributes schema))

;;
;; TODO
;; When the duplicates ns is deleted the same functions will be in the RAD library and we will delete these 2 defs and
;; make the calls to the RAD library directly
;;

(def schemas-for-delta dups/schemas-for-delta)
(def pathom-plugin dups/pathom-plugin)