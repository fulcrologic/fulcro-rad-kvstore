(ns com.example.components.seeded-connection
  (:require
    [mount.core :refer [defstate]]
    [com.example.model :refer [all-attributes]]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.example.components.config :as config]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write :refer [ident-of value-of]]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    ;; Just list all the adaptors here and the multi-method dynamic loading will work
    [com.fulcrologic.rad.database-adapters.key-value.redis]
    [com.example.components.seed :as seed]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.type-support.date-time :as dt]))

;;
;; We've got a tiny database so let's seed it every time we refresh
;; Far less confusing not to have this :on-reload thing - change the seed function and it will be run!
;; ^{:on-reload :noop}
;;
(defstate kv-connections
  "The connection to the database that has just been freshly populated"
  :start (let [{:keys [main] :as databases} {:main (key-value/start config/config)}]
           (seed/seed! main)
           databases))
