(ns com.example.components.database-queries
  (:require
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [taoensso.encore :as enc]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.read :as kv-read]
    [taoensso.timbre :as log]))

(defn get-all-accounts
  [env query-params]
  (if-let [db (some-> (get-in env [::key-value/databases :production]) deref)]
    (let [read-tree (partial kv-read/read-tree db env)]
      (if (:show-inactive? query-params)
        (kv-adaptor/read* db env :account/id)
        (->> (kv-adaptor/read* db env :account/id)
             (map read-tree)
             (filter :account/active?)
             (mapv #(select-keys % [:account/id])))))
    (throw (ex-info "No database atom for production schema!" {:env (keys env)}))))

(defn get-all-items
  [env {:category/keys [id]}]
  (if-let [db (some-> (get-in env [::key-value/databases :production]) deref)]
    (let [read-tree (partial kv-read/read-tree db env)]
      (if id
        (->> (kv-adaptor/read* db env :item/id)
             (map read-tree)
             (filter #(#{id} (-> % :item/category :category/id)))
             (mapv #(select-keys % [:item/id])))
        (kv-adaptor/read* db env :item/id)))
    (throw (ex-info "No database atom for production schema!" {:env (keys env)}))))

(defn get-customer-invoices [env {:account/keys [id]}]
  (if-let [db (some-> (get-in env [::key-value/databases :production]) deref)]
    (let [read-tree (partial kv-read/read-tree db env)]
      (->> (kv-adaptor/read* db env :invoice/id)
           (map read-tree)
           (filter #(= id (-> % :invoice/customer :account/id)))
           (mapv #(select-keys % [:invoice/id]))))
    (throw (ex-info "No database atom for production schema!" {:env (keys env)}))))

(defn get-all-invoices
  [env query-params]
  (if-let [db (some-> (get-in env [::key-value/databases :production]) deref)]
    (kv-adaptor/read* db env :invoice/id)
    (throw (ex-info "No database atom for production schema!" {:env (keys env)}))))

(defn get-invoice-customer-id
  [env invoice-id]
  (if-let [db (some-> (get-in env [::key-value/databases :production]) deref)]
    (-> (kv-adaptor/read* db {} [:invoice/id invoice-id])
        :invoice/customer
        second)
    (throw (ex-info "No database atom for production schema!" {:env (keys env)}))))

(defn get-all-categories
  [env query-params]
  (if-let [db (some-> (get-in env [::key-value/databases :production]) deref)]
    (kv-adaptor/read* db env :category/id)
    (throw (ex-info "No database atom for production schema!" {:env (keys env)}))))

(defn get-line-item-category
  [env line-item-id]
  (if-let [db (some-> (get-in env [::key-value/databases :production]) deref)]
    (let [i-id (-> (kv-adaptor/read* db {} [:line-item/id line-item-id]) :line-item/item second)
          c-id (-> (kv-adaptor/read* db {} [:item/id i-id]) :item/category second)]
      c-id)
    (throw (ex-info "No database atom for production schema!" {:env (keys env)}))))

(defn get-login-info-2
  "Get the account name, time zone, and password info via a username (email)."
  [{::key-value/keys [databases] :as env} username]
  (let [db @(:production databases)
        read-tree (partial kv-read/read-tree db env)
        account (->> (kv-adaptor/read* db env :account/id)
                     (map read-tree)
                     (filter #(= username (:account/email %)))
                     first)]
    ;; TODO
    ;; Make it crash on arrival before fixing
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
