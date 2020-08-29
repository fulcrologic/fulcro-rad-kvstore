(ns com.example.components.delete-middleware
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as mw]))

(def middleware (mw/wrap-delete))
