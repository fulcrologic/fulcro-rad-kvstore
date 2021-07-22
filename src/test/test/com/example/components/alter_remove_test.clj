(ns test.com.example.components.alter-remove-test
  (:require [clojure.test :refer :all]
            [com.fulcrologic.rad.database-adapters.key-value :as key-value]
            [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
            [com.example.components.seeded-connection :refer [kv-connections]]
            [com.example.components.seed :refer [all-tables! all-entities!]]
            [mount.core :as mount]
            [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
            [com.fulcrologic.rad.ids :refer [new-uuid]]
            [clojure.core.async :as async :refer [<!! <! chan go go-loop]]
            [konserve.core :as k]
            [com.fulcrologic.rad.database-adapters.strict-entity :as strict-entity]
            [com.fulcrologic.rad.database-adapters.key-value-options :as kvo]))

(defn env-key-store []
  (mount/start)
  (let [key-store (:main kv-connections)]
    [{kvo/databases {:production (atom key-store)}} key-store]))

(defn my-rand-nth
  "Helps tests to fail rather than error"
  [xs]
  (if (empty? xs)
    nil
    (rand-nth xs)))

(deftest alter-account
  (let [[env {::kv-key-store/keys [store table->rows] :as key-store}] (env-key-store)
        active-accounts-1 (->> (table->rows :account/id)
                               (filter :account/active?))
        altered-account (assoc (my-rand-nth active-accounts-1) :account/active? false)
        num-active-1 (count active-accounts-1)]
    (<!! (k/assoc-in store (strict-entity/entity->ident altered-account) altered-account))
    (let [active-accounts-2 (->> (table->rows :account/id)
                                 (filter :account/active?))
          num-active-2 (count active-accounts-2)]
      (is (= (- num-active-1 1) num-active-2))
      (kv-write/import key-store (all-tables!) (all-entities!)))))

(deftest remove1
  (let [[env {::kv-key-store/keys [store table->rows table->ident-rows] :as key-store}] (env-key-store)
        accounts-1 (table->ident-rows :account/id)
        [table id :as candidate-ident] (my-rand-nth accounts-1)
        num-1 (count accounts-1)]
    (<!! (k/update store table dissoc id))
    (let [accounts-2 (table->rows :account/id)
          num-2 (count accounts-2)]
      (is (= (dec num-1) num-2))
      (kv-write/import key-store (all-tables!) (all-entities!)))))

(deftest remove-all
  (let [[env {::kv-key-store/keys [store table->rows] :as key-store}] (env-key-store)
        accounts-1 (table->rows :account/id)
        candidate-account (my-rand-nth accounts-1)]
    (kv-write/remove-table-rows key-store env :account/id)
    (let [accounts-2 (table->rows :account/id)
          num-1 (count accounts-1)
          num-2 (count accounts-2)]
      (is (nil?
            (<!! (k/get-in store (strict-entity/entity->ident candidate-account)))))
      (is (zero? num-2))
      (is ((complement zero?) num-1))
      (kv-write/import key-store (all-tables!) (all-entities!)))))


