(ns com.example.components.parser
  (:require
    [com.example.components.auto-resolvers :refer [automatic-resolvers]]
    [com.example.components.seeded-connection :refer [kv-connection]]
    [com.example.components.config :refer [config]]
    [com.example.components.delete-middleware :as delete]
    [com.example.components.save-middleware :as save]
    [com.example.model :refer [all-attributes]]
    [com.example.model.account :as account]
    [com.example.model.invoice :as invoice]
    [com.example.model.timezone :as timezone]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.pathom :as pathom]
    [mount.core :refer [defstate]]
    [com.example.model.sales :as sales]
    [com.example.model.item :as item]
    [com.wsscode.pathom.core :as p]
    [com.fulcrologic.rad.type-support.date-time :as dt]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as key-value-pathom]))

(defstate parser
  :start
  (pathom/new-parser config
    [(attr/pathom-plugin all-attributes)
     (form/pathom-plugin save/middleware delete/middleware)
     (key-value-pathom/pathom-plugin (fn [env] {:production (:main kv-connection)}))
     {::p/wrap-parser
      (fn transform-parser-out-plugin-external [parser]
        (fn transform-parser-out-plugin-internal [env tx]
          (dt/with-timezone "America/Los_Angeles"
            (if (and (map? env) (seq tx))
              (parser env tx)
              {}))))}]
    [automatic-resolvers
     form/resolvers
     account/resolvers
     invoice/resolvers
     item/resolvers
     sales/resolvers
     timezone/resolvers]))
