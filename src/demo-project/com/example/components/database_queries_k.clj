(ns com.example.components.database-queries-k
  (:require
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.entity-read :as kv-entity-read]
    [taoensso.timbre :as log]
    [konserve.core :as k]
    [clojure.core.async :as async :refer [<!! chan go go-loop]]))

(defn get-all-accounts
  [[env db] query-params]
  (if (:show-inactive? query-params)
    (->> (keys (<!! (k/get-in db [:account/id])))
         (mapv (fn [id] {:account/id id})))
    (->> (vals (<!! (k/get-in db [:account/id])))
         (filter :account/active?)
         (mapv #(select-keys % [:account/id])))))

(defn get-all-items
  [[env db] {:category/keys [id]}]
  (assert false "get-all-items in k")
  (let [read-tree (kv-entity-read/read-tree-hof db env)]
    (if id
      (->> (kv-adaptor/read-table db env :item/id)
           (map read-tree)
           (filter #(#{id} (-> % :item/category :category/id)))
           (mapv #(select-keys % [:item/id])))
      (->> (kv-adaptor/read-table db env :item/id)
           (mapv (fn [[table id]] {table id}))))))

(defn get-customer-invoices [[env db] {:account/keys [id]}]
  (assert false "get-customer-invoices in k")
  (let [read-tree (kv-entity-read/read-tree-hof db env)]
    (->> (kv-adaptor/read-table db env :invoice/id)
         (map read-tree)
         (filter #(= id (-> % :invoice/customer :account/id)))
         (mapv #(select-keys % [:invoice/id])))))

(defn get-all-invoices
  [[env db] query-params]
  (assert false "get-all-invoices in k")
  (->> (kv-adaptor/read-table db env :invoice/id)
       (mapv (fn [[table id]] {table id}))))

(defn get-invoice-customer-id
  [[env db] invoice-id]
  (assert false "get-invoice-customer-id in k")
  (-> (kv-adaptor/read1 db env [:invoice/id invoice-id])
      :invoice/customer
      second))

(defn get-all-categories
  [[env db] query-params]
  (assert false "get-all-categories in k")
  (->> (kv-adaptor/read-table db env :category/id)
       (mapv (fn [[table id]] {table id}))))

(defn get-line-item-category
  [[env db] line-item-id]
  (assert false "get-line-item-category in k")
  (let [i-id (-> (kv-adaptor/read1 db env [:line-item/id line-item-id]) :line-item/item second)
        c-id (-> (kv-adaptor/read1 db env [:item/id i-id]) :item/category second)]
    c-id))

(defn get-login-info-2
  "Get the account name, time zone, and password info via a username (email)."
  [{::key-value/keys [databases] :as env} username]
  (assert false "get-login-info-2 in k")
  (let [db @(:production databases)
        read-tree (kv-entity-read/read-tree-hof db env)
        account (->> (kv-adaptor/read-table db env :account/id)
                     (map read-tree)
                     (filter #(= username (:account/email %)))
                     first)]
    (log/warn "account (TZ is key and s/be string)" (:time-zone/zone-id account))
    account))
