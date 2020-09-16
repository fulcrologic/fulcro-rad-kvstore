(ns play.com.example.components.auto-resolvers
  (:require
    [com.example.model :refer [all-attributes]]
    [mount.core :refer [defstate]]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]
    [au.com.seasoft.general.dev :as dev]))

(comment
  [{:com.fulcrologic.rad.attributes/type          :string,
    :com.fulcrologic.rad.attributes/qualified-key :account/name,}
   {:com.fulcrologic.rad.attributes/qualified-key :account/primary-address,
    :com.fulcrologic.rad.attributes/target        :address/id,
    :com.fulcrologic.rad.attributes/type          :ref}])

(defn x-1 []
  (-> (kv-pathom/generate-resolvers all-attributes :production)
      first
      dev/pp))
