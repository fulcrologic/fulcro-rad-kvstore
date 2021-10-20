(ns com.example.components.queries
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [taoensso.timbre :as log]
    [konserve.core :as k]
    [clojure.core.async :refer [<! go]]))

(defn get-all-accounts
  [{::kv-key-store/keys [store]}
   query-params]
  (go
    (if (:show-inactive? query-params)
      (->> (keys (<! (k/get-in store [:account/id])))
           (mapv (fn [id] {:account/id id})))
      (->> (vals (<! (k/get-in store [:account/id])))
           (filter :account/active?)
           (mapv #(select-keys % [:account/id]))))))

(defn get-all-items
  [{::kv-key-store/keys [store]}
   {:category/keys [id] :as query-params}]
  (go
    (if id
      (->> (vals (<! (k/get-in store [:item/id])))
           (filter #(#{id} (-> % :item/category second)))
           (mapv #(select-keys % [:item/id])))
      (->> (keys (<! (k/get-in store [:item/id])))
           (mapv (fn [id] {:item/id id}))))))

(defn get-customer-invoices
  [{::kv-key-store/keys [store]}
   {:account/keys [id] :as query-params}]
  (go
    (->> (vals (<! (k/get-in store [:invoice/id])))
         (filter #(= id (-> % :invoice/customer second)))
         (mapv #(select-keys % [:invoice/id])))))

(defn get-all-invoices
  [{::kv-key-store/keys [store]}]
  (go
    (->> (keys (<! (k/get-in store [:invoice/id])))
         (mapv (fn [id] {:invoice/id id})))))

(defn get-invoice-customer-id
  [{::kv-key-store/keys [store]}
   invoice-id]
  (go
    (-> (<! (k/get-in store [:invoice/id invoice-id]))
        :invoice/customer
        second)))

(defn get-all-categories
  [{::kv-key-store/keys [store]}]
  (go
    (->> (keys (<! (k/get-in store [:category/id])))
         (mapv (fn [id] {:category/id id})))))

(defn get-line-item-category
  [{::kv-key-store/keys [store]}
   line-item-id]
  (go
    (let [i-id (-> (<! (k/get-in store [:line-item/id line-item-id])) :line-item/item second)
          c-id (-> (<! (k/get-in store [:item/id i-id])) :item/category second)]
      c-id)))

;; Just created for testing
(defn get-all-line-items
  [{::kv-key-store/keys [store]}]
  (go
    (->> (keys (<! (k/get-in store [:line-item/id])))
         (mapv (fn [id] {:line-item/id id})))))

(defn get-login-info
  "Get the account name, time zone, and password info via a username (email)."
  [{::kv-key-store/keys [store]}
   username]
  (go
    (let [account (->> (vals (<! (k/get-in store [:account/id])))
                       (filter #(= username (:account/email %)))
                       first)]
      account)))