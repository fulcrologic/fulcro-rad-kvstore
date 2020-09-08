(ns test.com.example.components.alter-remove-test
  (:require [clojure.test :refer :all]
            [com.fulcrologic.rad.database-adapters.key-value :as key-value]
            [com.example.components.seeded-connection :refer [kv-connections all-tables! all-entities!]]
            [mount.core :as mount]
            [com.fulcrologic.rad.database-adapters.key-value.database :as kv-database]
            [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
            [com.fulcrologic.rad.ids :refer [new-uuid]]
            [clojure.core.async :as async :refer [<!! <! chan go go-loop]]
            [konserve.core :as k]))

(defn env-db []
  (mount/start)
  (let [conn (:main kv-connections)]
    [{::key-value/databases {:production (atom conn)}} conn]))

;; To prefer failures to errors when testing with kludge off
(defn my-rand-nth [xs]
  (if (empty? xs)
    nil
    (rand-nth xs)))

(deftest alter-account
  (let [[env {:keys [store read-table] :as db}] (env-db)
        active-accounts-1 (->> (read-table :account/id)
                               (filter :account/active?))
        altered-account (assoc (my-rand-nth active-accounts-1) :account/active? false)
        num-active-1 (count active-accounts-1)]
    ;(kv-adaptor/write1 db env (kv-write/entity->ident altered-account) altered-account)
    (<!! (k/assoc-in store (kv-write/entity->ident altered-account) altered-account))
    (let [active-accounts-2 (->> (read-table :account/id)
                                 (filter :account/active?))
          num-active-2 (count active-accounts-2)]
      (is (= (- num-active-1 1) num-active-2))
      (kv-database/destructive-reset db (all-tables!) (all-entities!)))))

(deftest remove1
  (let [[env {:keys [store read-table read-table-idents] :as db}] (env-db)
        accounts-1 (read-table-idents :account/id)
        [table id :as candidate-ident] (my-rand-nth accounts-1)
        num-1 (count accounts-1)]
    (<!! (k/update store table dissoc id))
    (let [accounts-2 (read-table :account/id)
          num-2 (count accounts-2)]
      (is (= (- num-1 1) num-2))
      (kv-database/destructive-reset db (all-tables!) (all-entities!)))))

(deftest remove-all
  (let [[env {:keys [store read-table] :as db}] (env-db)
        accounts-1 (read-table :account/id)
        candidate-account (my-rand-nth accounts-1)]
    (kv-write/remove-table-rows! db env :account/id)
    (let [accounts-2 (read-table :account/id)
          num-1 (count accounts-1)
          num-2 (count accounts-2)]
      (is (nil?
            (<!! (k/get-in store (kv-write/entity->ident candidate-account)))))
      (is (zero? num-2))
      (is ((complement zero?) num-1))
      (kv-database/destructive-reset db (all-tables!) (all-entities!)))))


