(ns com.fulcrologic.rad.database-adapters.key-value-options
  "No options here yet. So far for each database we have:
    :key-value/kind choose from: #{:redis :clojure-atom}
    :key-value/schema example: :production
    :key-value/dont-store-nils? either: true/false - When your domain doesn't allow nils, set to false, default is false
    :redis/uri example: \"redis://127.0.0.1:6379/\"
    :filestore/location example: \"/tmp/fulcro_rad_demo_db\"
   See one of the config .edn files for example cases, usually defaults.edn")