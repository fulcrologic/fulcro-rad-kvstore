(ns com.example.components.database-queries
  (:require
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [taoensso.encore :as enc]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.database :as kv-database]
    [taoensso.timbre :as log]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]
    [konserve.core :as k]
    [clojure.core.async :as async :refer [<!! <! chan go go-loop]]))

(defn context-f [env]
  (let [[db kind :as context] (kv-pathom/context-f :production ::key-value/databases env)]
    [(if (kv-database/konserve-stores kind)
       (kv-adaptor/store db)
       db)
     kind]))

(defn get-all-accounts-k
  [[env db] query-params]
  (<!!
    (go
      (if (:show-inactive? query-params)
        (->> (keys (<! (k/get-in db [:account/id])))
             (mapv (fn [id] {:account/id id})))
        (->> (vals (<! (k/get-in db [:account/id])))
             (filter :account/active?)
             (mapv #(select-keys % [:account/id])))))))

(defn get-all-accounts
  [env query-params]
  (when-let [[db kind :as context] (context-f env)]
    (get-all-accounts-k [env db] query-params)))

(defn get-all-items-k
  [[env db] {:category/keys [id]}]
  (<!!
    (go
      (if id
        (->> (vals (<! (k/get-in db [:item/id])))
             (filter #(#{id} (-> % :item/category second)))
             (mapv #(select-keys % [:item/id])))
        (->> (keys (<! (k/get-in db [:item/id])))
             (mapv (fn [id] {:item/id id})))))))

(defn get-all-items
  [env {:category/keys [id] :as query-params}]
  (when-let [[db kind :as context] (context-f env)]
    (get-all-items-k [env db] query-params)))

(defn get-customer-invoices-k
  [[env db] {:account/keys [id]}]
  (<!!
    (go
      (->> (vals (<! (k/get-in db [:invoice/id])))
           (filter #(= id (-> % :invoice/customer second)))
           (mapv #(select-keys % [:invoice/id]))))))

(defn get-customer-invoices [env {:account/keys [id] :as query-params}]
  (when-let [[db kind :as context] (context-f env)]
    (get-customer-invoices-k [env db] query-params)))

(defn get-all-invoices-k
  [[env db] query-params]
  (<!!
    (go
      (->> (keys (<! (k/get-in db [:invoice/id])))
           (mapv (fn [id] {:invoice/id id}))))))

(defn get-all-invoices
  [env query-params]
  (when-let [[db kind :as context] (context-f env)]
    (get-all-invoices-k [env db] query-params)))

(defn get-invoice-customer-id-k
  [[env db] invoice-id]
  (<!!
    (go
      (-> (<! (k/get-in db [:invoice/id invoice-id]))
          :invoice/customer
          second))))

(defn get-invoice-customer-id
  [env invoice-id]
  (when-let [[db kind :as context] (context-f env)]
    (get-invoice-customer-id-k [env db] invoice-id)))

(defn get-all-categories-k
  [[env db] query-params]
  (<!!
    (go
      (->> (keys (<! (k/get-in db [:category/id])))
           (mapv (fn [id] {:category/id id}))))))

(defn get-all-categories
  [env query-params]
  (when-let [[db kind :as context] (context-f env)]
    (get-all-categories-k [env db] query-params)))

(defn get-line-item-category-k
  [[env db] line-item-id]
  (<!!
    (go
      (let [i-id (-> (<! (k/get-in db [:line-item/id line-item-id])) :line-item/item second)
            c-id (-> (<! (k/get-in db [:item/id i-id])) :item/category second)]
        c-id))))

(defn get-line-item-category
  [env line-item-id]
  (when-let [[db kind :as context] (context-f env)]
    (get-line-item-category-k [env db] line-item-id)))

;; Just created for testing
(defn get-all-line-items
  [[env db] query-params]
  (<!!
    (go
      (->> (keys (<! (k/get-in db [:line-item/id])))
           (mapv (fn [id] {:line-item/id id}))))))

(defn get-login-info-2
  "Get the account name, time zone, and password info via a username (email)."
  [{::key-value/keys [databases] :as env} username]
  (let [db @(:production databases)
        ;store (kv-adaptor/store db)
        ]
    (<!!
      (go
        (let [account (->> (vals (<! (k/get-in (kv-adaptor/store db) [:account/id])))
                           (filter #(= username (:account/email %)))
                           first)]
          (log/warn "account (TZ is key and s/be string)" (:time-zone/zone-id account))
          account)))))

#_(defn get-login-info-2
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