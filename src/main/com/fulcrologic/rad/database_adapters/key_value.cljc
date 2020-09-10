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

    key-store (make-key-store
                (<!! (new-carmine-store uri))
                (str \"Konserve Redis at \" uri)
                {:key-value/dont-store-nils? true})

  However you wouldn't create directly like this if using from RAD. Instead set one of your databases to be the `:main`
  one in a configuration file (defaults.edn in the example project) and have a mount `defstate` that calls
  `::kv-key-store/start`. See the Demo project.

  `make-key-store` returns a `::key-value/keystore` which is a simple map that can be destructured:

    {:keys [store table->rows table->ident-rows ident->entity write-entity ids->entities]}

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

  Here both are equally async, but when things get more interesting choose to use `store` directly"
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.rad.database-adapters.strict-entity :as strict-entity]))

(def key-store? (every-pred map? :store :instance-name))

(s/def ::key-store key-store?)

(s/def ::idents (s/coll-of ::strict-entity/ident))

(s/def ::ids (s/coll-of ::strict-entity/id))

;;
;; To be checking that it is a strict entity every time would be too much
;;
(s/def ::entity map?)

;;
;; Konserve doesn't have spec AFAIK. We could do more than nothing however...
;;
(s/def ::store any?)

(s/def ::pair (s/tuple ::strict-entity/ident ::entity))

(s/def ::pairs-of-ident-map (s/coll-of ::pair :kind vector))

(s/def ::tables (s/coll-of ::strict-entity/table))

(s/def ::rows (s/coll-of ::entity))

(s/def ::table-id-entity-3-tuple (s/tuple ::strict-entity/table uuid? map?))

(s/def ::ident-s-or-table (s/or :ident ::strict-entity/ident
                                :idents ::idents
                                :table ::strict-entity/table))

