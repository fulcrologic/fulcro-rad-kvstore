(ns com.example.components.database-queries-k
  (:require
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.entity-read :as kv-entity-read]
    [taoensso.timbre :as log]
    [konserve.core :as k]
    [clojure.core.async :as async :refer [<!! <! chan go go-loop]]))

(defn get-all-accounts
  [[env db] query-params]
  (<!!
    (go
      (if (:show-inactive? query-params)
        (->> (keys (<! (k/get-in db [:account/id])))
             (mapv (fn [id] {:account/id id})))
        (->> (vals (<! (k/get-in db [:account/id])))
             (filter :account/active?)
             (mapv #(select-keys % [:account/id])))))))

(defn get-all-items
  [[env db] {:category/keys [id]}]
  (<!!
    (go
      (if id
        (->> (vals (<! (k/get-in db [:item/id])))
             (filter #(#{id} (-> % :item/category second)))
             (mapv #(select-keys % [:item/id])))
        (->> (keys (<! (k/get-in db [:item/id])))
             (mapv (fn [id] {:item/id id})))))))

(defn get-customer-invoices
  [[env db] {:account/keys [id]}]
  (<!!
    (go
      (->> (vals (<! (k/get-in db [:invoice/id])))
           (filter #(= id (-> % :invoice/customer second)))
           (mapv #(select-keys % [:invoice/id]))))))

(defn get-all-invoices
  [[env db] query-params]
  (<!!
    (go
      (->> (keys (<! (k/get-in db [:invoice/id])))
           (mapv (fn [id] {:invoice/id id}))))))

;; Just created for testing
(defn get-all-line-items
  [[env db] query-params]
  (<!!
    (go
      (->> (keys (<! (k/get-in db [:line-item/id])))
           (mapv (fn [id] {:line-item/id id}))))))

(defn get-invoice-customer-id
  [[env db] invoice-id]
  (<!!
    (go
      (-> (<! (k/get-in db [:invoice/id invoice-id]))
          :invoice/customer
          second))))

(defn get-all-categories
  [[env db] query-params]
  (<!!
    (go
      (->> (keys (<! (k/get-in db [:category/id])))
           (mapv (fn [id] {:category/id id}))))))

(defn get-line-item-category
  [[env db] line-item-id]
  (<!!
    (go
      (let [i-id (-> (<! (k/get-in db [:line-item/id line-item-id])) :line-item/item second)
            c-id (-> (<! (k/get-in db [:item/id i-id])) :item/category second)]
        c-id))))

(defn get-login-info-2
  "Get the account name, time zone, and password info via a username (email)."
  [{::key-value/keys [databases] :as env} username]
  (throw (ex-info "get-login-info-2 in k" {}))
  (let [db @(:production databases)
        read-tree (kv-entity-read/read-tree-hof db env)
        account (->> (kv-adaptor/read-table db env :account/id)
                     (map read-tree)
                     (filter #(= username (:account/email %)))
                     first)]
    (log/warn "account (TZ is key and s/be string)" (:time-zone/zone-id account))
    account))
