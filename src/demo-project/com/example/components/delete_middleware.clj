(ns com.example.components.delete-middleware
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]))

(def middleware (kv-pathom/wrap-delete))
