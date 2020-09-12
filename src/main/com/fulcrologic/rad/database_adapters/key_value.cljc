(ns com.fulcrologic.rad.database-adapters.key-value
  "This Fulcro RAD Key Value store adaptor library reads and writes data to a Konserve Key Store database.
  See Konserve (https://github.com/replikativ/konserve) for all possible implementations.
  Supporting a new database may mean a trivial 'include it' fix to
  this library, or contributing to Konserve. For instance at time of writing MongoDB
  was not supported.

  Base data is always stored with key being an
  `eql/ident` and the value a map of attributes, where the attribute values are either scalars or references. A
  reference can be either an ident or a vector of idents. If the data you want to store is not already strictly
  normalised in this way then see the function `::kv-write/write-tree`.

  Creation example:

    key-store (key-value/make-key-store
                (<!! (new-carmine-store uri))
                (str \"Konserve Redis at \" uri)
                {:key-value/dont-store-nils? true})

  However you wouldn't create directly like this if using from RAD. Instead set one of your databases to be the `:main`
  one in a configuration file (defaults.edn in the example project) and have a mount `defstate` that calls
  `key-value/start`. See the Demo project.

  `key-value/make-key-store` returns a `::key-value/keystore` which is a simple map that can be destructured:

    {::kv-key-store/keys [store table->rows table->ident-rows ident->entity write-entity ids->entities]}

  The simplest way to get started is to use these functions directly. `ident->entity` returns what is stored at
  the ident - a single entity map where all references are idents. `table->ident-rows` accepts a
  table keyword and returns all the rows in the table as a vector of idents. Here is how you would get all the
  active accounts (from the Demo project):

    (->> (table->rows :account/id)
         (filter :account/active?)
         (mapv #(select-keys % [:account/id])))

  Here's one way you could do the same thing using Konserve's store directly:

    (<!!
      (go
        (->> (vals (<! (k/get-in store [:account/id])))
             (filter :account/active?)
             (mapv #(select-keys % [:account/id])))))

  Here both are equally async, but when things get more interesting choose to use `store` directly.
  The 'adaptor' is the value of the key `::key-value/store` in ::key-value/key-store.
  We create the simple map here. Also other existential things like filling with data or getting all
  the data out. The word 'store' is what Konserve always uses in its documentation.
  It is their adaptor we are using, so following their naming convention"
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [com.fulcrologic.rad.database-adapters.strict-entity :as strict-entity]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [konserve.core :as k]
    [konserve.filestore :refer [new-fs-store]]
    [konserve.memory :refer [new-mem-store]]
    [clojure.core.async :as async :refer [<!! <! >! close! chan go go-loop]]
    [taoensso.timbre :as log]))

(s/def ::key-store (s/keys :req [::kv-key-store/store ::kv-key-store/instance-name]))

(s/def ::idents (s/coll-of ::strict-entity/ident))

(s/def ::ids (s/coll-of ::strict-entity/id))

;;
;; To be checking that it is a strict entity every time would be too much
;;
(s/def ::entity map?)

(s/def ::pair (s/tuple ::strict-entity/ident ::entity))

(s/def ::pairs-of-ident-map (s/coll-of ::pair :kind vector))

(s/def ::tables (s/coll-of ::strict-entity/table))

(s/def ::rows (s/coll-of ::entity))

(s/def ::table-id-entity-3-tuple (s/tuple ::strict-entity/table uuid? map?))

(s/def ::ident-s-or-table (s/or :ident ::strict-entity/ident
                                :idents ::idents
                                :table ::strict-entity/table))

(defmulti make-konserve-adaptor
          "We implement a couple here, but the more heavy duty ones each have their own jar file"
          (fn [adaptor-kind options]
            adaptor-kind))

(defmethod make-konserve-adaptor
  :filestore
  [_ {:filestore/keys [location] :as options}]
  [(str "Konserve file store at " location)
   (new-fs-store location)])

(defmethod make-konserve-adaptor
  :memory
  [_ _]
  [(str "Konserve memory store")
   (new-mem-store)])

;;
;; There are supposed to be bang versions
;; https://github.com/replikativ/konserve/issues/24
;; So not need to <!! here every time
;; Not documented in Konserve apart from a resolved issue.
;;
;; These functions are inside a ::key-value/key-store. That's why they are special and take a ::key-value/store
;; as an arg. Normally always pass around a ::key-value/key-store and de-structure to a store or one of these
;; functions
;;

(>defn table->rows
  "Get all the data for a particular table as a vector of maps"
  [store table]
  [::kv-key-store/store ::strict-entity/table => ::rows]
  (vec (vals (<!! (k/get-in store [table])))))

(>defn table->ident-rows
  "Instead of getting all the data, get all the rows but only in ident form"
  [store table]
  [::kv-key-store/store ::strict-entity/table => ::idents]
  (->> (keys (<!! (k/get-in store [table])))
       (mapv (fn [id] [table id]))))

(>defn ident->entity
  "Given an ident, fetch out a whole entity"
  [store ident]
  [::kv-key-store/store ::strict-entity/ident => ::entity]
  (<!! (k/get-in store ident)))

(>defn write-entity
  "Store an entity at its ident location"
  [store entity]
  [::kv-key-store/store ::entity => any?]
  (<!! (k/assoc-in store (strict-entity/entity->ident entity) entity)))

(>defn ids->entities
  "Getting a load of entities from idents at once (async) and putting them into an output chan"
  [store table ids]
  [::kv-key-store/store ::strict-entity/table ::ids => vector?]
  (let [out (chan (count ids))]
    (go
      (loop [ids ids]
        (when-let [id (first ids)]
          (let [entity (<! (k/get-in store [table id]))]
            (if entity
              (>! out entity)
              (log/warn "Could not find an entity from ident" [table id]))
            (recur (next ids)))))
      (close! out))
    (<!! (async/into [] out))))

(>defn make-key-store
  "Given a Konserve key value store (the real adaptor) create a map around it so we have access to its instance-name and
  options. Additionally we include some functions for common tasks. Full destructuring would be:
   `{::kv-key-store/keys [store table->rows table->ident-rows ident->entity write-entity ids->entities]}`
  Pass store to any `k/` functions. Otherwise always pass a ::key-value/key-store around"
  [store instance-name options]
  [::kv-key-store/store string? map? => ::key-store]
  {::kv-key-store/store             store
   ::kv-key-store/instance-name     instance-name
   ::kv-key-store/options           options
   ::kv-key-store/table->rows       (partial table->rows store)
   ::kv-key-store/table->ident-rows (partial table->ident-rows store)
   ::kv-key-store/ident->entity     (partial ident->entity store)
   ::kv-key-store/write-entity      (partial write-entity store)
   ::kv-key-store/ids->entities     (partial ids->entities store)})

(comment
  "Destructure like this and pass `store` to any k/ function"
  {::kv-key-store/keys [store table->rows table->ident-rows ident->entity write-entity ids->entities]})

(>defn start
  "Given a configuration map `start` returns a `::key-store`.
  It calls the multimethod `make-konserve-adaptor`, so make sure you have required the namespace that has the defmethod.
  This won't be an issue for `:kind` values `:memory` or `filestore`, as their defmethod-s are right here in this ns.

  Many databases can be in the config file. All except the `:main` one are just there for reference.
  Note that (unlike the datomic implementation) attributes are not passed into this function because there's no
  schema with Key Value databases, hence no automatic schema generation is possible"
  [{::keys [databases]}]
  [map? => ::key-store]
  (let [{:key-value/keys [kind dont-store-nils?]
         :or             {dont-store-nils? false}
         :as             main-database} (:main databases)]
    (when (nil? main-database)
      (throw (ex-info "Need to have a database called :main" {:names (keys databases)})))
    (when (nil? kind)
      (throw (ex-info ":kind not found in :main database\n" {:database main-database})))
    (let [[desc adaptor] (make-konserve-adaptor kind main-database)]
      (make-key-store (<!! adaptor) desc main-database))))

(>defn export
  "Sometimes useful to see the whole database at once"
  [{:keys [read-table]} env tables]
  [::key-store map? ::tables => map?]
  (into {} (for [table tables]
             (let [entities (vec (read-table table))]
               [table entities]))))

