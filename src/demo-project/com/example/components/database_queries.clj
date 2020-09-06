(ns com.example.components.database-queries
  (:require
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [taoensso.encore :as enc]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.entity-read :as kv-entity-read]
    [taoensso.timbre :as log]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]))

(def context-f (partial kv-pathom/context-f :production ::key-value/databases))

(defn get-all-accounts
  [env query-params]
  (when-let [[db kind :as context] (context-f env)]
    (let [read-tree (kv-entity-read/read-tree-hof db env)]
      (if (:show-inactive? query-params)
        (->> (kv-adaptor/read-table db env :account/id)
             (mapv (fn [[table id]] {table id})))
        (->> (kv-adaptor/read-table db env :account/id)
             (map read-tree)
             (filter :account/active?)
             (mapv #(select-keys % [:account/id])))))))

(defn get-all-items
  [env {:category/keys [id]}]
  (when-let [[db kind :as context] (context-f env)]
    (let [read-tree (kv-entity-read/read-tree-hof db env)]
      (if id
        (->> (kv-adaptor/read-table db env :item/id)
             (map read-tree)
             (filter #(#{id} (-> % :item/category :category/id)))
             (mapv #(select-keys % [:item/id])))
        (->> (kv-adaptor/read-table db env :item/id)
             (mapv (fn [[table id]] {table id})))))))

(defn get-customer-invoices [env {:account/keys [id]}]
  (when-let [[db kind :as context] (context-f env)]
    (let [read-tree (kv-entity-read/read-tree-hof db env)]
      (->> (kv-adaptor/read-table db env :invoice/id)
           (map read-tree)
           (filter #(= id (-> % :invoice/customer :account/id)))
           (mapv #(select-keys % [:invoice/id]))))))

(defn get-all-invoices
  [env query-params]
  (when-let [[db kind :as context] (context-f env)]
    (->> (kv-adaptor/read-table db env :invoice/id)
         (mapv (fn [[table id]] {table id})))))

(defn get-invoice-customer-id
  [env invoice-id]
  (when-let [[db kind :as context] (context-f env)]
    (-> (kv-adaptor/read1 db env [:invoice/id invoice-id])
        :invoice/customer
        second)))

(defn get-all-categories
  [env query-params]
  (when-let [[db kind :as context] (context-f env)]
    (->> (kv-adaptor/read-table db env :category/id)
         (mapv (fn [[table id]] {table id})))))

(defn get-line-item-category
  [env line-item-id]
  (when-let [[db kind :as context] (context-f env)]
    (let [i-id (-> (kv-adaptor/read1 db env [:line-item/id line-item-id]) :line-item/item second)
          c-id (-> (kv-adaptor/read1 db env [:item/id i-id]) :item/category second)]
      c-id)))

(defn get-login-info-2
  "Get the account name, time zone, and password info via a username (email)."
  [{::key-value/keys [databases] :as env} username]
  (let [db @(:production databases)
        read-tree (kv-entity-read/read-tree-hof db env)
        account (->> (kv-adaptor/read-table db env :account/id)
                     (map read-tree)
                     (filter #(= username (:account/email %)))
                     first)]
    (log/warn "account (TZ is key and s/be string)" (:time-zone/zone-id account))
    account))

(defn d-pull [db pull eid]
  (log/error "datomic pull with id" pull eid))

;;
;; Keeping to show that above we are not outputting the name of the time-zone
;; (rather the keyword)
;;
(defn get-login-info-1
  "Get the account name, time zone, and password info via a username (email)."
  [{::key-value/keys [databases] :as env} username]
  (enc/if-let [db @(:production databases)]
              (d-pull db [:account/name
                          {:time-zone/zone-id [:db/ident]}
                          :password/hashed-value
                          :password/salt
                          :password/iterations]
                      [:account/email username])))
