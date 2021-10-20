(ns com.example.components.database-queries
  "`server-queries` would be a better name but constrained by no alterations to model allowed, so we can always easily
  copy over the latest RAD Demo"
  (:require
    [com.example.components.queries :as queries]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [taoensso.encore :as enc]
    [taoensso.timbre :as log]
    [konserve.core :as k]
    [clojure.core.async :refer [<!! <! go]]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]))

(defn get-all-accounts-1
  [env query-params]
  (when-let [key-store (kv-pathom/env->key-store env)]
    (<!!
      (queries/get-all-accounts key-store query-params))))

(defn get-all-accounts-2
  [env query-params]
  (when-let [{::kv-key-store/keys [table->ident-rows table->rows]} (kv-pathom/env->key-store env)]
    (if (:show-inactive? query-params)
      (->> (table->ident-rows :account/id)
           (mapv (fn [[table id]] {table id})))
      (->> (table->rows :account/id)
           (filter :account/active?)
           (mapv #(select-keys % [:account/id]))))))

(def get-all-accounts get-all-accounts-1)

(defn get-all-items
  [env query-params]
  (when-let [key-store (kv-pathom/env->key-store env)]
    (<!!
      (queries/get-all-items key-store query-params))))

(defn get-customer-invoices
  [env query-params]
  (when-let [key-store (kv-pathom/env->key-store env)]
    (<!!
      (queries/get-customer-invoices key-store query-params))))

(defn get-all-invoices
  [env query-params]
  (when-let [key-store (kv-pathom/env->key-store env)]
    (<!!
      (queries/get-all-invoices key-store))))

(defn get-invoice-customer-id
  [env invoice-id]
  (when-let [key-store (kv-pathom/env->key-store env)]
    (<!!
      (queries/get-invoice-customer-id key-store invoice-id))))

(defn get-all-categories
  [env query-params]
  (when-let [key-store (kv-pathom/env->key-store env)]
    (<!!
      (queries/get-all-categories key-store))))

(defn get-line-item-category
  [env line-item-id]
  (when-let [key-store (kv-pathom/env->key-store env)]
    (<!!
      (queries/get-line-item-category key-store line-item-id))))

;; Just created for testing
(defn get-all-line-items
  [env query-params]
  (when-let [key-store (kv-pathom/env->key-store env)]
    (<!!
      (queries/get-all-line-items key-store))))

(defn get-login-info
  "Get the account name, time zone, and password info via a username (email)."
  [{::key-value/keys [databases] :as env} username]
  (let [key-store @(:production databases)]
    (<!!
      (queries/get-login-info key-store username))))

(defn d-pull [db pull eid]
  (log/error "datomic pull with id" pull eid))

;;
;; Keeping to show that above we are not outputting the name of the time-zone
;; (rather the keyword)
;; Not working code as has :db/ident in it which is Datomic specific!
;;
(defn get-login-info-2
  "Get the account name, time zone, and password info via a username (email)."
  [{::key-value/keys [databases] :as env} username]
  (enc/if-let [db @(:production databases)]
              (d-pull db [:account/name
                          {:time-zone/zone-id [:db/ident]}
                          :password/hashed-value
                          :password/salt
                          :password/iterations]
                      [:account/email username])))