(ns play.com.example.components.parser
  (:require
    [com.example.components.parser :as parser]
    [mount.core :refer [defstate]]
    [com.example.components.seeded-connection :refer [kv-connections all-tables!]]
    [general.dev :as dev]
    [com.example.model :refer [all-attributes]]
    [com.example.components.auto-resolvers :refer [automatic-resolvers]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
    [clojure.core.async :as async :refer [<!! <! chan go go-loop]]
    [konserve.core :as k]))

(defn env [] {})

(defn conn [] (:main kv-connections))

(defn read-from-connection []
  (let [{:keys [ident->entity]} (conn)]
    (dev/pp (ident->entity [:account/id #uuid "ffffffff-ffff-ffff-ffff-000000000100"]))))

(defn env-before-use-parser []
  (let [env {:parser               parser/parser
             ::attr/schema         :production
             ::attr/key->attribute (attr/attribute-map all-attributes)}]
    env))

(defn invoke-play-parser [env query]
  (->> query
       (parser/parser env)))

(defn scalar-query []
  (let [env (env-before-use-parser)
        res (invoke-play-parser env [{[:item/id (new-uuid 200)]
                                      [:item/id :item/name :item/price]}])]
    (->> res
         dev/pp)))

(defn to-one-join-query []
  (let [env (env-before-use-parser)
        res (invoke-play-parser env [{[:account/id (new-uuid 100)]
                                      [:account/role
                                       :account/email
                                       :account/name
                                       :account/active?
                                       :account/role
                                       {:account/primary-address [:address/street :address/city :address/state]}
                                       ]}])]
    (->> res
         dev/pp)))

(defn to-many-join-query []
  (let [env (env-before-use-parser)
        res (invoke-play-parser env [{[:account/id (new-uuid 100)]
                                      [:account/role
                                       :account/email
                                       :account/name
                                       :account/active?
                                       {:account/addresses [:address/street :address/city :address/state]}
                                       ]}])]
    (->> res
         dev/pp)))

(defn view-current-connection []
  (let [{:keys [instance-name]} (conn)]
    instance-name))

(defn entire-db []
  (dev/pp (kv-adaptor/export (conn) (env) (all-tables!))))

(defn test-remove-all []
  (kv-write/remove-table-rows! (conn) (env) :account/id))

(defn wipe-database []
  (let [db (conn)]
    (doseq [table (all-tables!)]
      (kv-write/remove-table-rows! db (env) table))))

(defn all-addresses []
  (let [{:keys [table-rows]} (conn)]
    (table-rows :address/id)))

(defn all-line-items []
  (let [{:keys [table-rows]} (conn)]
    (table-rows :line-item/id)))

(defn all-accounts []
  (let [{:keys [table-rows]} (conn)]
    (dev/pp (table-rows :account/id))))

(defn every-line-item-expanded []
  (let [{:keys [table-rows]} (conn)]
    (->> (table-rows :line-item/id)
         dev/pp)))

(defn all-item-ids-where-cat-id []
  (let [{:keys [table-rows]} (conn)
        toys-cat-id (new-uuid 1002)]
    (->> (table-rows :item/id)
         (filterv #(#{toys-cat-id} (-> % :item/category second)))
         dev/pp)))

;(d-q '[:find [?uuid ...]
;       :in $ ?cid
;       :where
;       [?dbid :invoice/id ?uuid]
;       [?dbid :invoice/customer ?c]
;       [?c :account/id ?cid]] db id)
(defn all-invoices-of-an-account []
  (let [cid (new-uuid 103)
        {:keys [table-rows]} (conn)]
    (->> (table-rows :invoice/id)
         (filterv #(= cid (-> % :invoice/customer second)))
         dev/pp)))

;(d-q '[:find ?account-uuid .
;       :in $ ?invoice-uuid
;       :where
;       [?i :invoice/id ?invoice-uuid]
;       [?i :invoice/customer ?c]
;       [?c :account/id ?account-uuid]] db invoice-id)

(defn given-invoice-get-customer []
  (let [{:keys [table-rows]} (conn)]
    (-> (rand-nth (table-rows :invoice/id))
        :invoice/customer)))

(defn given-line-item-get-category []
  (let [{:keys [table-ident-rows ident->entity]} (conn)
        li-ident (rand-nth (table-ident-rows :line-item/id))
        i-id (-> (ident->entity li-ident) :line-item/item second)
        c-id (-> (ident->entity [:item/id i-id]) :item/category second)]
    c-id))

(defn alter-test []
  (let [{:keys [table-rows write-entity]} (conn)
        active-accounts-1 (->> (table-rows :account/id)
                               (filter :account/active?))
        altered-account (assoc (rand-nth active-accounts-1) :account/active? false)
        num-active-1 (count active-accounts-1)]
    (write-entity altered-account)
    (let [active-accounts-2 (->> (table-rows :account/id)
                                 (filter :account/active?))
          num-active-2 (count active-accounts-2)]
      [num-active-1 num-active-2])))

