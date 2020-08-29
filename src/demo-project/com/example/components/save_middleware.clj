(ns com.example.components.save-middleware
  (:require
    [com.fulcrologic.rad.middleware.save-middleware :as r.s.middleware]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as mw]))

(def middleware
  (->
    (mw/wrap-save)
    (r.s.middleware/wrap-rewrite-values)))
