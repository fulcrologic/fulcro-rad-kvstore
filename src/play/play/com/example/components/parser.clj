(ns play.com.example.components.parser
  (:require
    [com.example.components.parser :as parser]
    [mount.core :refer [defstate]]
    [com.example.components.seeded-connection :refer [kv-connection all-tables!]]
    [general.dev :as dev]
    [com.example.model :refer [all-attributes]]
    [com.example.components.auto-resolvers :refer [automatic-resolvers]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.entity-read :as kv-entity-read]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]))

(defn env [] {})

(defn read-from-connection []
  (let [conn (:main kv-connection)]
    (dev/pp (kv-pathom/read-compact conn (env) [:account/id #uuid "ffffffff-ffff-ffff-ffff-000000000100"]))))

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
  (let [db (:main kv-connection)]
    (kv-adaptor/instance-name-f db)))

(defn whole-db []
  (let [db (:main kv-connection)
        env (env)]
    (doseq [table (all-tables!)]
      (let [entities (->> (kv-adaptor/read-table db env table)
                          (map (fn [m]
                                 (let [ident [table (get m table)]]
                                   (kv-adaptor/read1 db env ident)))))]
        (dev/log-on table)
        (dev/pp entities)))))

(defn test-remove-all []
  (let [db (:main kv-connection)]
    (kv-write/remove-table-rows! db (env) :account/id)))

(defn wipe-database []
  (let [db (:main kv-connection)]
    (doseq [table (all-tables!)]
      (kv-write/remove-table-rows! db (env) table))))

(defn all-anything [table]
  (let [db (:main kv-connection)
        read-tree (kv-entity-read/read-tree-hof db (env))]
    (->> (kv-adaptor/read-table db (env) table)
         (map read-tree)
         dev/pp)))

(defn all-addresses []
  (all-anything :address/id))

(defn all-line-items []
  (all-anything :line-item/id))

(defn all-accounts []
  (all-anything :account/id))

(defn every-line-item-expanded []
  (let [db (:main kv-connection)
        read-tree (kv-entity-read/read-tree-hof db (env))]
    (->> (kv-adaptor/read-table db (env) :line-item/id)
         (map read-tree)
         dev/pp)))

(defn all-item-ids-where-cat-id []
  (let [db (:main kv-connection)
        read-tree (kv-entity-read/read-tree-hof db (env))
        toys-cat-id (new-uuid 1002)]
    (->> (kv-adaptor/read-table db (env) :item/id)
         (map read-tree)
         (filter #(#{toys-cat-id} (-> % :item/category :category/id)))
         dev/pp)))

;(d-q '[:find [?uuid ...]
;       :in $ ?cid
;       :where
;       [?dbid :invoice/id ?uuid]
;       [?dbid :invoice/customer ?c]
;       [?c :account/id ?cid]] db id)
(defn all-invoices-of-an-account []
  (let [cid (new-uuid 103)
        db (:main kv-connection)
        read-tree (kv-entity-read/read-tree-hof db (env))]
    (->> (kv-adaptor/read-table db (env) :invoice/id)
         (map read-tree)
         (filter #(= cid (-> % :invoice/customer :account/id)))
         dev/pp)))

;(d-q '[:find ?account-uuid .
;       :in $ ?invoice-uuid
;       :where
;       [?i :invoice/id ?invoice-uuid]
;       [?i :invoice/customer ?c]
;       [?c :account/id ?account-uuid]] db invoice-id)

(defn given-invoice-get-customer []
  (let [db (:main kv-connection)
        env (env)
        iid (:invoice/id (rand-nth (kv-adaptor/read-table db env :invoice/id)))]
    (-> (kv-adaptor/read1 db env [:invoice/id iid]))))

(defn given-line-item-get-category []
  (let [env (env)
        db (:main kv-connection)
        li-id (:line-item/id (rand-nth (kv-adaptor/read-table db env :line-item/id)))
        i-id (-> (kv-adaptor/read1 db env [:line-item/id li-id]) :line-item/item second)
        c-id (-> (kv-adaptor/read1 db env [:item/id i-id]) :item/category second)]
    c-id))
