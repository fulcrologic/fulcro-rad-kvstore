(ns com.fulcrologic.rad.database-adapters.key-value
  "This Fulcro RAD Key Value store adaptor library reads and writes data to a Konserve Key Store database.
  See Konserve (https://github.com/replikativ/konserve) for all possible implementations.
  Supporting a new database may mean a trivial 'include it' fix to
  this library, or contributing to Konserve. For instance at time of writing MongoDB
  was not supported. Of course there's no hard dependency on Konserve.

  Base data is always stored with key being an
  `eql/ident` and the value a map of attributes, where the attribute values are either scalars or references. A
  reference can be either an ident or a vector of idents. If the data you want to store is not already strictly
  normalised in this way then see the function `::kv-write/write-tree`.

  Creation example:

    key-store (key-value/make-key-store
                (<!! (new-carmine-store uri))
                (str \"Konserve Redis at \" uri)
                {:key-value/dont-store-nils? true}
                false)

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
  The 'adaptor' is the value of the key `::kv-key-store/store` in ::key-value/key-store.
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
    [com.fulcrologic.rad.database-adapters.key-value.konserve :as kv-konserve]
    #?(:clj [clojure.core.async :as async :refer [<!! <! >! close! chan go]])
    #?(:cljs [cljs.core.async :as async :refer [<! >! close! chan go]])
    [taoensso.timbre :as log]))

(s/def ::key-store (s/keys :req [::kv-key-store/store ::kv-key-store/instance-name]))

(s/def ::pair (s/tuple ::strict-entity/ident ::strict-entity/entity))

(s/def ::pairs-of-ident-map (s/coll-of ::pair :kind vector))

(s/def ::table-id-entity-3-tuple (s/tuple ::strict-entity/table uuid? map?))

(s/def ::ident-s-or-table (s/or :ident ::strict-entity/ident
                                :idents (s/coll-of ::strict-entity/ident)
                                :table ::strict-entity/table))

#?(:clj
   (>defn table->rows
     "Get all the data for a particular table as a vector of maps. Blocking"
     [store table]
     [::kv-key-store/store ::strict-entity/table => (s/coll-of ::strict-entity/entity)]
     (<!! (kv-konserve/table->rows store table))))

#?(:clj
   (>defn table->ident-rows
     "Instead of getting all the data, get all the rows but only in ident form. Blocking"
     [store table]
     [::kv-key-store/store ::strict-entity/table => (s/coll-of ::strict-entity/ident)]
     (<!! (kv-konserve/table->ident-rows store table))))

#?(:clj
   (>defn ident->entity
     "Given an ident, fetch out a whole entity. Blocking"
     [store ident]
     [::kv-key-store/store ::strict-entity/ident => ::strict-entity/entity]
     (<!! (kv-konserve/ident->entity store ident))))

#?(:clj
   (>defn write-entity
     "Store an entity at its ident location. Block on the result so it happens synchronously"
     [store entity]
     [::kv-key-store/store ::strict-entity/entity => any?]
     (<!! (kv-konserve/write-entity store entity))))

#?(:clj
   (>defn ids->entities
     "Getting a load of entities from idents at once (async) and putting them into an output chan. Blocking
     to return"
     [store table ids]
     [::kv-key-store/store ::strict-entity/table (s/coll-of ::strict-entity/id) => vector?]
     (<!! (kv-konserve/ids->entities store table ids))))

(>defn make-key-store
  "Given a Konserve key value store (the real adaptor) create a map around it so we have access to its instance-name and
  options. Additionally we include some functions for common tasks. Full destructuring would be:
   `{::kv-key-store/keys [store table->rows table->ident-rows ident->entity write-entity ids->entities]}`
  Pass store to any `k/` functions. Otherwise always pass a ::key-value/key-store around.
  `store` should not be a chan, but we are not checking for this.
  If chan-fns? then chan versions of the functions - so for each of these fns the result is there but it is just
  in a chan and your calling code will have to get it out using `<!`, which of course can only be used within a
  `go` block"
  [store instance-name options chan-fns?]
  [::kv-key-store/store string? map? boolean? => ::key-store]
  (let [fns-m (if chan-fns?
                {::kv-key-store/table->rows       (partial kv-konserve/table->rows store)
                 ::kv-key-store/table->ident-rows (partial kv-konserve/table->ident-rows store)
                 ::kv-key-store/ident->entity     (partial kv-konserve/ident->entity store)
                 ::kv-key-store/write-entity      (partial kv-konserve/write-entity store)
                 ::kv-key-store/ids->entities     (partial kv-konserve/ids->entities store)}
                #?(:clj  {::kv-key-store/table->rows       (partial table->rows store)
                          ::kv-key-store/table->ident-rows (partial table->ident-rows store)
                          ::kv-key-store/ident->entity     (partial ident->entity store)
                          ::kv-key-store/write-entity      (partial write-entity store)
                          ::kv-key-store/ids->entities     (partial ids->entities store)}
                   :cljs {}))]
    (merge {::kv-key-store/store         store
            ::kv-key-store/instance-name instance-name
            ::kv-key-store/options       options}
           fns-m)))

(defn config->options
  "Given standard RAD config find out the options map for the `:main` one"
  [config]
  (let [{::keys [databases]} config
        {:key-value/keys [kind dont-store-nils?]
         :or             {dont-store-nils? false}
         :as             main-database} (:main databases)]
    (when (nil? main-database)
      (throw (ex-info "Need to have a database called :main" {:names            (keys databases)
                                                              :keys-from-config (and config (keys config))
                                                              :config-nil?      (nil? config)})))
    (when (nil? kind)
      (throw (ex-info ":kind not found in :main database\n" {:database main-database})))
    main-database))

(defn make-key-store-async
  "The adaptor is a channel that holds one thing that we can only get out once!
  So here we create/get it and put it somewhere (put-f), in a go block, which have to do because <!!
  is not allowed in cljs"
  [kind options put-f]
  (when (-> put-f fn? not)
    (throw (ex-info "Expected put-f to be a function" {:kind kind :options options})))
  (let [[desc adaptor-ch] (kv-key-store/make-adaptor kind options)]
    (go
      (let [adaptor (<! adaptor-ch)
            key-store (make-key-store adaptor desc options true)]
        (put-f key-store)))
    nil))

(comment
  "Destructure like this and pass `store` to any k/ function"
  {::kv-key-store/keys [store table->rows table->ident-rows ident->entity write-entity ids->entities]})

#?(:clj
   (>defn start
     "Given a configuration map `start` returns a `::key-store`.
     It calls the multimethod `kv-key-store/make-adaptor`, so make sure you have required the namespace that has the defmethod.
     This won't be an issue for `:kind` values `:memory`, `:indexeddb` or `filestore`, as their defmethod-s are right
     here in this ns.

     Many databases can be in the config file. All except the `:main` one are just there for reference.
     Note that (unlike the datomic implementation) attributes are not passed into this function because there's no
     schema with Key Value databases, hence no automatic schema generation is possible"
     ([config]
      [map? => ::key-store]
      (let [{:key-value/keys [kind] :as options} (config->options config)
            [desc adaptor] (kv-key-store/make-adaptor kind options)]
        (make-key-store (<!! adaptor) desc options false)))))

(>defn start-async
  "Same as `start` but the functions that come in the `::key-store` are async, so you need to pull the value out
  of the channel with `<!`.

  `put-f` is for putting the adaptor somewhere you can easily read it back from"
  [config put-f]
  [map? fn? => nil?]
  (let [{:key-value/keys [kind] :as options} (config->options config)]
    (make-key-store-async kind options put-f)))

(>defn export
  "Sometimes useful to see the whole database at once"
  [{:keys [read-table]} env tables]
  [::key-store map? (s/coll-of ::strict-entity/table) => map?]
  (into {} (for [table tables]
             (let [entities (vec (read-table table))]
               [table entities]))))

