(ns com.fulcrologic.rad.database-adapters.key-value.pathom
  (:require [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
            [com.fulcrologic.rad.database-adapters.key-value.memory :as memory-adaptor]
            [com.fulcrologic.rad.database-adapters.key-value.redis_2 :as redis-adaptor]
            [clojure.pprint :refer [pprint]]
            [com.fulcrologic.rad.database-adapters.key-value :as key-value]
            [com.fulcrologic.rad.database-adapters.key-value.read :as key-value-read]
            [com.fulcrologic.rad.attributes :as attr]
            [com.fulcrologic.rad.form :as form]
            [com.wsscode.pathom.core :as p]
            [com.rpl.specter :as sp]
            [taoensso.encore :as enc]
            [com.wsscode.pathom.connect :as pc]
            [com.fulcrologic.guardrails.core :refer [>defn => ?]]
            [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
            [com.fulcrologic.rad.authorization :as auth]
            [edn-query-language.core :as eql]
            [clojure.spec.alpha :as s]
            [com.fulcrologic.rad.database-adapters.key-value.write :as key-value-write]
            [taoensso.timbre :as log]))

;; TODO: Move comments like this to ns docstring or fn docstring. Ppl rarely look at source
;; We always return a map containing only one database - the :main one.
;; Not like the datomic implementation of the same function, that will return many databases.
;; So many databases might be in the config file, enabling us to easily switch one of them to be :main
;;
(defn start-database [_ {::key-value/keys [databases config]}]
  (let [{:key-value/keys [kind] :as main-database} (:main databases)]
    (assert main-database ["Have one database, but it isn't called :main" databases])
    (assert kind ["kind not found in key-value-keys\n" main-database])
    {:main (case kind
             :clojure-atom (memory-adaptor/->MemoryKeyStore "MemoryKeyStore" (atom {}))
             :redis (let [{:redis/keys [uri]} main-database
                          conn {:pool {} :spec {:uri uri}}
                          {:key-value/keys [table-kludge?]} config]
                      (redis-adaptor/->RedisKeyStore conn table-kludge?)))}))

(defn pathom-plugin
  "A pathom plugin that adds the necessary KeyStore connections/databases (same thing) to the pathom env for
  a given request. Requires a database-mapper, which is a
  `(fn [pathom-env] {schema-name connection})` for a given request.

  The resulting pathom-env available to all resolvers will then have:

  - `::key-value/connections`: The result of database-mapper
  - `::key-value/databases`: A map from schema name to atoms holding a database. The atom is present so that
  a mutation that modifies the database can choose to update the snapshot of the db being used for the remaining
  resolvers.

  This plugin should run before (be listed after) most other plugins in the plugin chain since
  it adds connection details to the parsing env.
  "
  [database-mapper]
  (p/env-wrap-plugin
    (fn [env]
      (let [database-connection-map (database-mapper env)
            databases (sp/transform [sp/MAP-VALS] (fn [v] (atom v)) database-connection-map)]
        (assoc env
          ::key-value/connections database-connection-map
          ::key-value/databases databases)))))

(def keys-in-delta
  (fn keys-in-delta [delta]
    (let [id-keys (into #{}
                        (map first)
                        (keys delta))
          all-keys (into id-keys
                         (mapcat keys)
                         (vals delta))]
      all-keys)))

(defn schemas-for-delta [{::attr/keys [key->attribute]} delta]
  (let [all-keys (keys-in-delta delta)
        schemas (into #{}
                      (keep #(-> % key->attribute ::attr/schema))
                      all-keys)]
    schemas))

(defn tempids->generated-ids [{::attr/keys [key->attribute] :as env} delta]
  (let [idents (keys delta)
        fulcro-tempid->generated-id (into {} (keep (fn [[k id :as ident]]
                                                     (when (tempid/tempid? id)
                                                       [id (key-value-read/unwrap-id env k id)])) idents))]
    fulcro-tempid->generated-id))

(defn tempid->intermediate-id [{::attr/keys [key->attribute]} delta]
  (let [tempids (set (sp/select (sp/walker tempid/tempid?) delta))
        fulcro-tempid->real-id (into {} (map (fn [t] [t (str (:id t))]) tempids))]
    fulcro-tempid->real-id))

(>defn delta->tempid-maps
  [env delta]
  [map? map? => map?]
  (let [tempid->txid (tempid->intermediate-id env delta)
        tempid->generated-id (tempids->generated-ids env delta)]
    {:tempid->string       tempid->txid
     :tempid->generated-id tempid->generated-id}))

(defn save-form!
  "Do all of the possible operations for the given form delta (save to all Key Value databases involved)"
  [env {::form/keys [delta] :as save-params}]
  (let [schemas (schemas-for-delta env delta)
        result (atom {:tempids {}})]
    (log/debug "Saving form across " schemas)
    (doseq [schema schemas
            :let [connection (-> env ::key-value/connections (get schema))
                  {:keys [tempid->string
                          tempid->generated-id]} (delta->tempid-maps env delta)]]
      (log/debug "Saving form delta" (with-out-str (pprint delta)))
      (log/debug "on schema" schema)
      (if connection
        (try
          (key-value-write/write-delta connection env delta)
          (let [tempid->real-id (into {}
                                      (map (fn [tempid] [tempid (get tempid->generated-id tempid)]))
                                      (keys tempid->string))]
            ;; Datomic needs to read the world as moved forward by this transaction. No similar concept b/c we
            ;; are always at the most recent.
            ;(when database-atom
            ;  (reset! database-atom (d/db connection)))
            (swap! result update :tempids merge tempid->real-id))
          (catch Exception e
            (log/error e "Transaction failed!")
            {}))
        (log/error "Unable to save form. Connection missing in env.")))
    @result))

(defn delete-entity!
  "Delete the given entity, if possible."
  [{::attr/keys [key->attribute] :as env} params]
  (enc/if-let [pk (ffirst params)
               id (get params pk)
               ident [pk id]
               {:keys [::attr/schema]} (key->attribute pk)
               connection (-> env ::key-value/connections (get schema))]
              (do
                (log/warn "Deleting (not yet tested)" ident)
                (kv-adaptor/remove1 connection env ident)
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
     (let [save-result (save-form! pathom-env params)
           handler-result (handler pathom-env)]
       (deep-merge save-result handler-result)))))

(defn wrap-delete
  "Form delete middleware to accomplish deletes."
  ([handler]
   (fn [{::form/keys [params] :as pathom-env}]
     (let [local-result (delete-entity! pathom-env params)
           handler-result (handler pathom-env)]
       (deep-merge handler-result local-result))))
  ([]
   (fn [{::form/keys [params] :as pathom-env}]
     (delete-entity! pathom-env params))))

(defn get-by-ids
  [db env idents]
  (mapv (fn [ident]
          (key-value-read/read-compact db env ident))
        idents))

(defn entity-query
  [{::key-value/keys [id-attribute]
    ::attr/keys    [schema attributes]
    :as            env}
   input]
  (let [{::attr/keys [qualified-key]} id-attribute
        one? (not (sequential? input))]
    (let [db (some-> (get-in env [::key-value/databases schema]) deref)
          _ (assert (satisfies? kv-adaptor/KeyStore db) ["db is not a KeyStore" schema db (keys (get env ::key-value/databases))])
          ids (if one?
                [(get input qualified-key)]
                (into [] (keep #(get % qualified-key)) input))
          idents (mapv (fn [id]
                         [qualified-key (key-value-read/unwrap-id env qualified-key id)])
                       ids)]
      (when (-> (kv-adaptor/db-f db env) count zero?)
        (log/warn "Empty key value store" (kv-adaptor/instance-name-f db env)))
      (let [result (get-by-ids db env idents)]
        (if one?
          (first result)
          result)))))

(defn- fix-id-keys
  "Fix the ID keys recursively on result."
  [k->a ast-nodes result]
  (assert (map? result) ["Expect fix-id-keys to be called on a map" (type result) result])
  (let [id? (fn [{:keys [dispatch-key]}] (some-> dispatch-key k->a ::attr/identity?))
        id-key (:key (sp/select-first [sp/ALL id?] ast-nodes))
        join-key->children (into {}
                                 (comp
                                   (filter #(= :join (:type %)))
                                   (map (fn [{:keys [key children]}] [key children])))
                                 ast-nodes)
        join-keys (set (keys join-key->children))
        join-key? #(contains? join-keys %)]
    (reduce-kv
      (fn [m k v]
        (cond
          (= :db/id k) (assoc m id-key v)
          (and (join-key? k) (vector? v)) (assoc m k (mapv #(fix-id-keys k->a (join-key->children k) %) v))
          (and (join-key? k) (map? v)) (assoc m k (fix-id-keys k->a (join-key->children k) v))
          :otherwise (assoc m k v)))
      {}
      result)))

(>defn key-value-result->pathom-result
  "Convert a query result into a pathom result"
  [k->a pathom-query result]
  [(s/map-of keyword? ::attr/attribute) ::eql/query (? coll?) => (? coll?)]
  (when result
    (let [{:keys [children]} (eql/query->ast pathom-query)]
      (if (vector? result)
        (mapv #(fix-id-keys k->a children %) result)
        (fix-id-keys k->a children result)))))

(defn id-resolver
  "Generates a resolver from `id-attribute` to the `output-attributes`."
  [all-attributes
   {::attr/keys [qualified-key] :keys [::attr/schema ::key-value/wrap-resolve] :as id-attribute}
   output-attributes]
  [::attr/attributes ::attr/attribute ::attr/attributes => ::pc/resolver]
  (let [outputs (attr/attributes->eql output-attributes)
        resolve-sym (symbol
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
                            (log/debug "In resolver:" qualified-key "inputs:" input)
                            (->> (entity-query
                                   (assoc env
                                     ::attr/schema schema
                                     ::attr/attributes output-attributes
                                     ::key-value/id-attribute id-attribute)
                                   input)
                                 (key-value-result->pathom-result key->attribute outputs)
                                 (auth/redact env)))
                          wrap-resolve (wrap-resolve)
                          :always (with-resolve-sym))}))

(defn generate-resolvers
  "Generate all of the resolvers that make sense for the given database config. This should be passed
  to your Pathom parser to register resolvers for each of your schemas."
  [attributes schema]
  (let [attributes (filter #(= schema (::attr/schema %)) attributes)
        key->attribute (attr/attribute-map attributes)
        entity-id->attributes (group-by ::k (mapcat (fn [attribute]
                                                      (map
                                                        (fn [id-key] (assoc attribute ::k id-key))
                                                        (get attribute ::attr/identities)))
                                                    attributes))
        entity-resolvers (reduce-kv
                           (fn [result k v]
                             (enc/if-let [attr (key->attribute k)
                                          resolver (id-resolver attributes attr v)]
                                         (conj result resolver)
                                         (do
                                           (log/error "Internal error generating resolver for ID key" k)
                                           result)))
                           []
                           entity-id->attributes)]
    entity-resolvers))