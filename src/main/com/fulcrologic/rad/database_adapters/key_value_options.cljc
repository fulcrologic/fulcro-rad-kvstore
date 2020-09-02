(ns com.fulcrologic.rad.database-adapters.key-value-options
  "No options here yet. So far for each database we have:
    :key-value/kind choose from: #{:redis :clojure-atom}
    :key-value/schema example: :production
    :key-value/table-kludge? either: true/false - Whether collect information so know about table rows, useful for querying
    :redis/uri example: \"redis://127.0.0.1:6379/\"
   See one of the config .edn files for example cases, usually defaults.edn")