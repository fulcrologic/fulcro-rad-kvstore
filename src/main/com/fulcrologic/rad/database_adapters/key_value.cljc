(ns com.fulcrologic.rad.database-adapters.key-value
  "This Key Value store adaptor library reads/writes data from/to a KeyStore, where a KeyStore is a supported database.
  So far there are KeyStore implementations for 'clojure atom' and Redis. Base data is always stored with key an
  `eql/ident` and the value a map of attributes, where the attribute values are either scalars or references. A
  reference can be either an ident or a vector of idents. If the data you want to store is not already strictly
  normalised in this way then see the function `::kv-write/write-tree`.

  Creation examples:

    ks (memory-adaptor/->MemoryKeyStore \"human consumption identifier\" (atom {}) {})
    adaptor (redis-adaptor/->RedisKeyStore {:pool {} :spec {:uri \"redis://127.0.0.1:6379/\"}} {:key-value/table-kludge? true})

  However you wouldn't create directly like this if using from RAD. Instead set one of your databases to be the `:main`
  one in a configuration file (defaults.edn in the example project) and have a mount `defstate` that calls
  `::kv-database/start`.

  The most primitive way to read is using the protocol directly. `::read1` returns what is stored at the ident - a
  single entity map where all references are idents. `::read*` is the plural version. `::read-table` accepts a
  table keyword and returns all the rows in the table as a vector of idents. `::read-table` works well with
  `::kv-entity-read/read-tree`. For example:

    (let [read-tree (kv-entity-read/read-tree-hof db env)]
      (->> (kv-adaptor/read-table db env :account/id)
           (map read-tree)
           (filter :account/active?)
           (mapv #(select-keys % [:account/id]))))

  In this namespace references that are input and output are always idents."
  (:require
    [clojure.spec.alpha :as s]))

(def key-store? (every-pred map? :store :instance-name))

(s/def ::key-store key-store?)

(def id-keyword? #(and (namespace %) (= "id" (name %))))

(s/def ::id-keyword id-keyword?)

(s/def ::table id-keyword?)

(s/def ::ident (s/tuple ::table uuid?))

(s/def ::idents (s/coll-of ::ident :kind vector?))

(s/def ::pair (s/tuple ::ident map?))

(s/def ::pairs-of-ident-map (s/coll-of ::pair))

(s/def ::tables (s/coll-of ::table :kind vector?))

(s/def ::table-id-entity-3-tuple (s/tuple ::table uuid? map?))

(s/def ::ident-s-or-table (s/or :ident ::ident
                                :idents ::idents
                                :table ::table))

