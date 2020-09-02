(ns play.com.fulcrologic.rad.database-adapters.key-value.pathom
  (:require
    [com.example.model.seed :as seed]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write :refer [ident-of value-of]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.attributes :as attr]
    [com.example.model :refer [all-attributes]]
    [general.dev :as dev]
    [com.example.components.config :as config]
    [com.fulcrologic.rad.database-adapters.key-value.database :as kv-database]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]))

(defn x-1 []
  (let [retire-erick {[:account/id (new-uuid 100)]
                      {:account/active? {:before true, :after false}}}
        key->attribute (attr/attribute-map all-attributes)
        env {::attr/key->attribute key->attribute}
        schemas (kv-pathom/schemas-for-delta env retire-erick)]
    (assert (seq schemas) "No schemas")
    schemas))

(defn alter-existing-user []
  (let [{:keys [main]} (kv-database/start-database config/config)
        address (seed/new-address (new-uuid 1) "111 Main St.")
        erick (seed/new-account (new-uuid 100) "Erick" "erick@example.com" "letmein"
                                :account/addresses [(ident-of (seed/new-address (new-uuid 1) "111 Main St."))]
                                :account/primary-address (value-of (seed/new-address (new-uuid 300) "222 Other"))
                                :time-zone/zone-id :time-zone.zone-id/America-Los_Angeles)]
    (doseq [[table id value] [address erick]]
      (kv-write/write-tree main {} value))
    (let [retire-erick {[:account/id (new-uuid 100)]
                        {:account/active? {:before true, :after false}}}
          key->attribute (attr/attribute-map all-attributes)
          env {::attr/key->attribute   key->attribute
               ::key-value/connections {:production main}}
          params {::form/delta retire-erick}
          tempids-map (kv-pathom/save-form! env params)
          retired-erick (kv-adaptor/read1 main env [:account/id (new-uuid 100)])]
      (dev/pp [tempids-map (:account/active? retired-erick)]))))

(defn new-user-delta [user-tempid address-tempid]
  {[:account/id user-tempid]
   {:account/name      {:before nil, :after "Chris"},
    :account/role      {:before nil, :after nil},
    :account/email     {:before nil, :after "chris@somemail.com.au"},
    :account/active?   {:before true, :after true},
    :account/primary-address
                       {:before nil,
                        :after  [:address/id address-tempid]},
    :account/addresses {:before [], :after []}},
   [:address/id address-tempid]
   {:address/street {:before nil, :after nil},
    :address/city   {:before nil, :after nil},
    :address/state  {:before nil, :after nil},
    :address/zip    {:before nil, :after nil}}})

(defn add-new-user []
  (let [user-tempid (tempid/tempid #uuid "ab067a98-ff75-4ea6-ab45-f3c72070a2a9")
        address-tempid (tempid/tempid #uuid "bf7cc6bb-bfdf-44e7-8deb-992224ab8b16")
        {:keys [main]} (kv-database/start-database config/config)
        delta (new-user-delta user-tempid address-tempid)
        key->attribute (attr/attribute-map all-attributes)
        env {::attr/key->attribute   key->attribute
             ::key-value/connections {:production main}}
        params {::form/delta delta}
        tempids-map (kv-pathom/save-form! env params)]
    (dev/pp tempids-map)))
