(ns com.example.components.save-middleware
  (:require
    [com.fulcrologic.rad.middleware.save-middleware :as r.s.middleware]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]))

(def middleware
  (->
    (kv-pathom/wrap-save)
    (r.s.middleware/wrap-rewrite-values)))
