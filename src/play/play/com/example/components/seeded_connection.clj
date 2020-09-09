(ns play.com.example.components.seeded-connection
  (:require [general.dev :as dev]
            [com.example.model.seed :as seed]
            [com.fulcrologic.rad.ids :refer [new-uuid]]
            [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write :refer [ident-of value-of]]
            [com.fulcrologic.rad.database-adapters.strict-entity :as strict-entity]))

(defn x-1 []
  (let [[table id value] (seed/new-account (new-uuid 100) "Tony" "tony@example.com" "letmein"
                                           :account/addresses [(ident-of (seed/new-address (new-uuid 1) "111 Main St."))]
                                           :account/primary-address (value-of (seed/new-address (new-uuid 300) "222 Other"))
                                           :time-zone/zone-id :time-zone.zone-id/America-Los_Angeles)]
    (dev/pp value)
    ;(dev/pp (tree-seq (some-fn eql/ident? map?) identity value))
    (dev/pp (kv-write/flatten (assoc value (kv-write/gen-protected-id!) [(strict-entity/entity->ident value) value])))))
