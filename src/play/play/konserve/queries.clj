(ns play.konserve.queries
  (:require
    [clojure.core.async :as async :refer [<!! chan go go-loop]]
    [com.example.components.seeded-connection :refer [kv-connections]]
    [com.example.components.database-queries :as queries]
    [mount.core :as mount]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [au.com.seasoft.general.dev :as dev]
    [com.fulcrologic.rad.database-adapters.key-value-options :as kvo]))

(defn env []
  (mount/start)
  (let [conn (:main kv-connections)]
    {kvo/databases {:production (atom conn)}}))

(defn x-1 []
  (dev/pp (queries/get-all-accounts-2 (env) {})))

(defn x-2 []
  (dev/pp (queries/get-all-items (env) {:category/id (new-uuid 1000)})))

(defn x-3 []
  (dev/pp (queries/get-customer-invoices (env) {:account/id (new-uuid 103)})))

(defn x-4 []
  (dev/pp (queries/get-all-invoices (env) {})))

(defn x-5 []
  (let [env (env)
        random-invoice (rand-nth (queries/get-all-invoices env {}))]
    (queries/get-invoice-customer-id env (:invoice/id random-invoice))))

(defn x-6 []
  (dev/pp (queries/get-all-categories (env) {})))

(defn x-7 []
  (let [env (env)
        random-line-item (rand-nth (queries/get-all-line-items env {}))]
    (queries/get-line-item-category env (:line-item/id random-line-item))))


