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
    [com.fulcrologic.rad.database-adapters.key-value.database :as kv-database]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]
    [clojure.core.async :as async :refer [<!! <! chan go go-loop]]
    [konserve.core :as k]))

(defn env [] {})

(defn conn [] (:main kv-connections))

(defn read-from-connection []
  (dev/pp (kv-database/ident->entity (conn) [:account/id #uuid "ffffffff-ffff-ffff-ffff-000000000100"])))

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
  (kv-adaptor/instance-name (conn)))

(defn entire-db []
  (dev/pp (kv-database/export (conn) (env) (all-tables!))))

(defn test-remove-all []
  (kv-write/remove-table-rows! (conn) (env) :account/id))

(defn wipe-database []
  (let [db (conn)]
    (doseq [table (all-tables!)]
      (kv-write/remove-table-rows! db (env) table))))

(defn all-addresses []
  (kv-database/read-table (conn) :address/id))

(defn all-line-items []
  (kv-database/read-table (conn) :line-item/id))

(defn all-accounts []
  (dev/pp (kv-database/read-table (conn) :account/id)))

(defn every-line-item-expanded []
  (let [db (conn)]
    (->> (kv-database/read-table db :line-item/id)
         dev/pp)))

(defn all-item-ids-where-cat-id []
  (let [db (conn)
        toys-cat-id (new-uuid 1002)]
    (->> (kv-database/read-table db :item/id)
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
        db (conn)]
    (->> (kv-database/read-table db :invoice/id)
         (filterv #(= cid (-> % :invoice/customer second)))
         dev/pp)))

;(d-q '[:find ?account-uuid .
;       :in $ ?invoice-uuid
;       :where
;       [?i :invoice/id ?invoice-uuid]
;       [?i :invoice/customer ?c]
;       [?c :account/id ?account-uuid]] db invoice-id)

(defn given-invoice-get-customer []
  (let [db (conn)
        env (env)]
    (-> (rand-nth (kv-database/read-table db :invoice/id))
        :invoice/customer)))

(defn given-line-item-get-category []
  (let [env (env)
        db (conn)
        li-ident (rand-nth (kv-database/read-table-idents db :line-item/id))
        i-id (-> (kv-database/ident->entity db li-ident) :line-item/item second)
        c-id (-> (kv-database/ident->entity db [:item/id i-id]) :item/category second)]
    c-id))

(defn alter-test []
  (let [db (conn)
        active-accounts-1 (->> (kv-database/read-table db :account/id)
                               (filter :account/active?))
        altered-account (assoc (rand-nth active-accounts-1) :account/active? false)
        num-active-1 (count active-accounts-1)]
    (kv-database/write-entity db altered-account)
    (let [active-accounts-2 (->> (kv-database/read-table db :account/id)
                                 (filter :account/active?))
          num-active-2 (count active-accounts-2)]
      [num-active-1 num-active-2])))

