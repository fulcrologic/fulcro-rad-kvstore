(ns play.konserve.queries
  (:require
    [clojure.core.async :as async :refer [<!! chan go go-loop]]
    [com.example.components.seeded-connection :refer [kv-connections]]
    [com.example.components.database-queries-k :as queries-k]
    [mount.core :as mount]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [general.dev :as dev]))

(defn env []
  (mount/start)
  (let [conn (:main kv-connections)]
    {::key-value/databases {:production (atom conn)}}))

(defn context-f []
  (let [[db kind :as context] (kv-pathom/context-f :production ::key-value/databases (env))]
    [(if (= :konserve kind)
       (kv-adaptor/store db)
       db)
     kind]))

(defn x-1 []
  (let [[fs] (context-f)]
    (dev/pp (queries-k/get-all-accounts [{} fs] {}))))

(defn x-2 []
  (let [[fs] (context-f)]
    (dev/pp (queries-k/get-all-items [{} fs] {:category/id (new-uuid 1000)}))))

(defn x-3 []
  (let [[fs] (context-f)]
    (dev/pp (queries-k/get-customer-invoices [{} fs] {:account/id (new-uuid 103)}))))

(defn x-4 []
  (let [[fs] (context-f)]
    (dev/pp (queries-k/get-all-invoices [{} fs] {}))))

(defn x-5 []
  (let [[fs] (context-f)
        random-invoice (rand-nth (queries-k/get-all-invoices [{} fs] {}))]
    (queries-k/get-invoice-customer-id [{} fs] (:invoice/id random-invoice))))

(defn x-6 []
  (let [[fs] (context-f)]
    (dev/pp (queries-k/get-all-categories [{} fs] {}))))

(defn x-7 []
  (let [[fs] (context-f)
        random-line-item (rand-nth (queries-k/get-all-line-items [{} fs] {}))]
    (queries-k/get-line-item-category [{} fs] (:line-item/id random-line-item))))


