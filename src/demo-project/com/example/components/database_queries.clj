(ns com.example.components.database-queries
  (:require
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [taoensso.encore :as enc]
    [taoensso.timbre :as log]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]
    [konserve.core :as k]
    [clojure.core.async :as async :refer [<!! <! chan go go-loop]]))

(defn env->db [env]
  (let [[db kind :as context] (kv-pathom/context-f :production ::key-value/databases env)]
    db))

(defn get-all-accounts
  [env query-params]
  (when-let [{:keys [store]} (env->db env)]
    (<!!
      (go
        (if (:show-inactive? query-params)
          (->> (keys (<! (k/get-in store [:account/id])))
               (mapv (fn [id] {:account/id id})))
          (->> (vals (<! (k/get-in store [:account/id])))
               (filter :account/active?)
               (mapv #(select-keys % [:account/id]))))))))

(defn get-all-items
  [env {:category/keys [id] :as query-params}]
  (when-let [{:keys [store]} (env->db env)]
    (<!!
      (go
        (if id
          (->> (vals (<! (k/get-in store [:item/id])))
               (filter #(#{id} (-> % :item/category second)))
               (mapv #(select-keys % [:item/id])))
          (->> (keys (<! (k/get-in store [:item/id])))
               (mapv (fn [id] {:item/id id}))))))))

(defn get-customer-invoices
  [env {:account/keys [id] :as query-params}]
  (when-let [{:keys [store]} (env->db env)]
    (<!!
      (go
        (->> (vals (<! (k/get-in store [:invoice/id])))
             (filter #(= id (-> % :invoice/customer second)))
             (mapv #(select-keys % [:invoice/id])))))))

(defn get-all-invoices
  [env query-params]
  (when-let [{:keys [store]} (env->db env)]
    (<!!
      (go
        (->> (keys (<! (k/get-in store [:invoice/id])))
             (mapv (fn [id] {:invoice/id id})))))))

(defn get-invoice-customer-id
  [env invoice-id]
  (when-let [{:keys [store]} (env->db env)]
    (<!!
      (go
        (-> (<! (k/get-in store [:invoice/id invoice-id]))
            :invoice/customer
            second)))))

(defn get-all-categories
  [env query-params]
  (when-let [{:keys [store]} (env->db env)]
    (<!!
      (go
        (->> (keys (<! (k/get-in store [:category/id])))
             (mapv (fn [id] {:category/id id})))))))

(defn get-line-item-category
  [env line-item-id]
  (when-let [{:keys [store]} (env->db env)]
    (<!!
      (go
        (let [i-id (-> (<! (k/get-in store [:line-item/id line-item-id])) :line-item/item second)
              c-id (-> (<! (k/get-in store [:item/id i-id])) :item/category second)]
          c-id)))))

;; Just created for testing
(defn get-all-line-items
  [env query-params]
  (when-let [{:keys [store]} (env->db env)]
    (<!!
      (go
        (->> (keys (<! (k/get-in store [:line-item/id])))
             (mapv (fn [id] {:line-item/id id})))))))

(defn get-login-info-2
  "Get the account name, time zone, and password info via a username (email)."
  [{::key-value/keys [databases] :as env} username]
  (let [{:keys [store]} @(:production databases)]
    (<!!
      (go
        (let [account (->> (vals (<! (k/get-in store [:account/id])))
                           (filter #(= username (:account/email %)))
                           first)]
          (log/warn "account (TZ is key and s/be string)" (:time-zone/zone-id account))
          account)))))

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