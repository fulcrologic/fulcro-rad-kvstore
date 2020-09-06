(ns com.fulcrologic.rad.database-adapters.key-value.pathom
  "Entry points for Pathom"
  (:require [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
            [clojure.pprint :refer [pprint]]
            [com.fulcrologic.rad.database-adapters.key-value :as key-value]
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
            [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
            [com.fulcrologic.rad.database-adapters.key-value.pathom-k :as pathom-k]
            [com.example.components.database-queries :as queries]
            [taoensso.timbre :as log]))

(defn pathom-plugin
  "A pathom plugin that adds the necessary `KeyStore` connections/databases (same thing) to the pathom env for
  a given request. Requires a database-mapper, which is a
  `(fn [pathom-env] {schema-name connection})` for a given request.

  The resulting pathom-env available to all resolvers will then have:

  - `::key-value/connections`: The result of database-mapper
  - `::key-value/databases`: A map from schema name to atoms holding a database. The atom is present so that
  a mutation that modifies the database can choose to update the snapshot of the db being used for the remaining
  resolvers.

  This plugin should run before (be listed after) most other plugins in the plugin chain since
  it adds connection details to the parsing env.

  This code is similar to the datomic-plugin's. However currently only one connection is supported, no sharding."
  [database-mapper]
  (p/env-wrap-plugin
    (fn [env]
      (let [database-connection-map (database-mapper env)
            databases-1 (sp/transform [sp/MAP-VALS] (fn [v] (atom v)) database-connection-map)
            databases-2 (into {}
                              (map (fn [[k v]]
                                     [k (atom v)]))
                              database-connection-map)]
        (when (not= (-> databases-1 first key) (-> databases-2 first key))
          (throw (ex-info "databases-1 and databases-2 not equal" {:databases-1 databases-1 :databases-2 databases-2})))
        (assoc env
          ::key-value/connections database-connection-map
          ::key-value/databases databases-2)))))

(defn- keys-in-delta
  "Copied from or very similar to datomic function of same name"
  [delta]
  (let [id-keys (into #{}
                      (map first)
                      (keys delta))
        all-keys (into id-keys
                       (mapcat keys)
                       (vals delta))]
    all-keys))

(defn schemas-for-delta
  "Copied from or very similar to datomic function of same name"
  [{::attr/keys [key->attribute]} delta]
  (let [all-keys (keys-in-delta delta)
        schemas (into #{}
                      (keep #(-> % key->attribute ::attr/schema))
                      all-keys)]
    schemas))

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

(defn tempids->generated-ids
  "Copied from or very similar to datomic function of same name"
  [{::attr/keys [key->attribute] :as env} delta]
  (let [idents (keys delta)
        fulcro-tempid->generated-id (into {} (keep (fn [[k id :as ident]]
                                                     (when (tempid/tempid? id)
                                                       [id (unwrap-id env k id)])) idents))]
    fulcro-tempid->generated-id))

(defn tempids-in-delta [delta]
  (into #{} (keep (fn [[table id :as ident]]
                    (when (tempid/tempid? id)
                      id))
                  (keys delta))))

(defn tempid->intermediate-id
  "Copied from or very similar to datomic function of same name, except rid of specter use"
  [delta]
  (let [tempids-1 (set (sp/select (sp/walker tempid/tempid?) delta))
        tempids-2 (tempids-in-delta delta)
        _ (when (not= tempids-1 tempids-2)
            (throw (ex-info "tempids-1 and tempids-2 not equal" {:tempids-1 tempids-1 :tempids-2 tempids-2})))
        fulcro-tempid->real-id (into {} (map (fn [t] [t (str (:id t))]) tempids-2))]
    fulcro-tempid->real-id))

(>defn delta->tempid-maps
  "Copied from or very similar to datomic function of same name"
  [env delta]
  [map? map? => map?]
  (let [tempid->txid (tempid->intermediate-id delta)
        tempid->generated-id (tempids->generated-ids env delta)]
    {:tempid->string       tempid->txid
     :tempid->generated-id tempid->generated-id}))

;;
;; TODO
;; Currently the atom is serving no purpose with only one connection.
;; Is only one connection fine?
;; If so ::key-value/connections -> ::key-value/connection and same with databases, or get rid of databases
;; altogether, as there is no difference between a connection and a database with KeyStore.
;; Multiple databases can exist in the config file, but don't need to be in pathom env.
;; However I suspect the answer is need to support multiple databases, and doesn't matter that connection is same as
;; a database - the goal is to have it work same as the Datomic database adapter - support multiple databases at
;; runtime.
;;
(defn save-form!
  "Do all of the possible operations for the given form delta (save to the Key Value database involved)"
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
          (kv-write/write-delta connection env delta)
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
        (log/error "Unable to save form. Connection missing in env." (keys env))))
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

(defn read-compact
  "Reads once from the database using `::kv-adaptor/read1` then transforms the ident joins into /id only (ident-like) maps"
  [ks env ident]
  (let [entity (kv-adaptor/read1 ks env ident)]
    (when entity
      (into {}
            (map (fn [[k v]]
                   (if (nil? v)
                     (do
                       (log/warn "`::kv-pathom/read-compact` nil value in database for attribute" k)
                       [k v])
                     [k ((idents->value-hof env) v)])))
            entity))))

(defn entity-query
  "Performs the query of the Key Value database. Uses the id-attribute that needs to be resolved and the input to the
  resolver which will contain that id/s the need to be queried for"
  [{::key-value/keys [id-attribute]
    ::attr/keys      [schema attributes]
    :as              env}
   input]
  (let [{::attr/keys [qualified-key]} id-attribute
        one? (not (sequential? input))
        [env db kind :as context] (queries/context env)]
    (let [read-compact (if (= kind :konserve)
                         pathom-k/read-compact
                         read-compact)
          ids (if one?
                [(get input qualified-key)]
                (into [] (keep #(get % qualified-key)) input))
          idents (mapv (fn [id]
                         [qualified-key (unwrap-id env qualified-key id)])
                       ids)]
      (when (= kind :konserve)
        (log/warn "Can improve by putting this in a go-loop (and won't need read-compact at all)"))
      (let [result (mapv #(read-compact db env %) idents)]
        (if one?
          (first result)
          result)))))

(defn first-identity-hof [k->a]
  (fn [ast-nodes]
    (->> ast-nodes
         (filter (fn [{:keys [dispatch-key]}]
                   (some-> dispatch-key k->a ::attr/identity?)))
         first
         :key)))

(>defn fix-id-keys
  "Fix the ID keys recursively on result."
  [k->a ast-nodes result]
  [map? vector? map? => map?]
  (let [first-identity (first-identity-hof k->a)
        id? (fn [{:keys [dispatch-key]}] (some-> dispatch-key k->a ::attr/identity?))
        id-key-1 (:key (sp/select-first [sp/ALL id?] ast-nodes))
        id-key-2 (first-identity ast-nodes)
        _ (when (not= id-key-1 id-key-2)
            (throw (ex-info "id-key-1 and id-key-2 not equal" {:id-key-1 id-key-1 :id-key-2 id-key-2})))
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
          (and (join-key? k) (vector? v)) (assoc m k (mapv #(fix-id-keys k->a (join-key->children k) %) v))
          (and (join-key? k) (map? v)) (assoc m k (fix-id-keys k->a (join-key->children k) v))
          :otherwise (assoc m k v)))
      {}
      result)))

(>defn key-value-result->pathom-result
  "Convert a query result into a pathom result"
  [k->a pathom-query result]
  [(s/map-of qualified-keyword? ::attr/attribute) ::eql/query (? coll?) => (? coll?)]
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