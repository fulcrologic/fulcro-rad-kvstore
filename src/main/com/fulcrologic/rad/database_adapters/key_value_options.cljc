(ns com.fulcrologic.rad.database-adapters.key-value-options
  "For each database we have:
    :key-value/kind choose from: #{:memory :filestore :indexeddb :redis :couchdb :leveldb :postgresql :riak}
    :key-value/schema example: :production
    :key-value/dont-store-nils? either: true/false - When your domain doesn't allow nils, set to false, default is false
    :redis/uri example: \"redis://127.0.0.1:6379/\"
    :filestore/location example: \"/tmp/fulcro_rad_demo_db\"
   See one of the config .edn files for example cases, usually defaults.edn")

;; Non attribute options

(def connections
  "If using the key-value pathom-plugin, the resulting pathom-env will contain
    a map from schema->connection at this key path"
  :com.fulcrologic.rad.database-adapters.key-value/connections)

(def databases
  "If using the key-value pathom-plugin, the resulting pathom-env will contain
    a map from schema->database at this key path"
  :com.fulcrologic.rad.database-adapters.key-value/databases)