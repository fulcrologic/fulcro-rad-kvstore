(ns com.example.components.auto-resolvers
  (:require
    [com.example.model :refer [all-attributes]]
    [mount.core :refer [defstate]]
    [com.fulcrologic.rad.resolvers :as res]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]))

(defstate automatic-resolvers
  :start
  (vec
    (concat
      (res/generate-resolvers all-attributes)
      (kv-pathom/generate-resolvers all-attributes :production))))
