(ns com.fulcrologic.rad.database-adapters.key-value.konserve
  ""
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]))

(deftype KonserveKeyStore [keystore-name a options] kv-adaptor/KeyStoreK
  (-k-instance-name [this]
    keystore-name)
  (-k-options [this]
    options)
  (-k-store [this]
    a))