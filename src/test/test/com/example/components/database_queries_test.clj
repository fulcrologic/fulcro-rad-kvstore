(ns test.com.example.components.database-queries-test
  (:require
    [clojure.test :refer :all]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [com.example.components.database-queries :as queries]
    [com.example.components.seeded-connection :refer [kv-connections]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [clojure.core.async :as async :refer [<!! <! chan go go-loop]]
    [mount.core :as mount]
    [au.com.seasoft.general.dev :as dev]))

(deftest always-passes
  (let [amount 1000]
    (is (= 1000 amount))))

(defn env
  "Queries get the Pathom env. So here we mock it, as not using Pathom"
  []
  (mount/start)
  (let [conn (:main kv-connections)]
    {::key-value/databases {:production (atom conn)}}))

;; To prefer failures to errors when testing with kludge off
(defn my-rand-nth [xs]
  (if (empty? xs)
    nil
    (rand-nth xs)))

;;
;; range includes the first but not the last
;;

(deftest all-accounts
  (let [expected-set (->> (range 100 104)
                          (map new-uuid)
                          (map (fn [id] {:account/id id}))
                          set)
        result-set (set (queries/get-all-accounts-2 (env) {:show-inactive? false}))]
    (is (= (set expected-set) result-set))
    ;(dev/pp result-set)
    ))

(deftest items-of-category
  (let [expected-set (->> (range 201 204)
                          (map new-uuid)
                          (map (fn [id] {:item/id id}))
                          set)
        result-set (set (queries/get-all-items (env) {:category/id (new-uuid 1000)}))]
    (is (= expected-set result-set))
    ;(dev/pp result-set)
    ))

(deftest customer-invoices
  (let [result-set (set (queries/get-customer-invoices (env) {:account/id (new-uuid 102)}))]
    (is (= 2 (count result-set)))
    (is (every? :invoice/id result-set))
    ;(dev/pp result-set)
    ))

(deftest all-invoices
  (let [result-set (set (queries/get-all-invoices (env) {}))]
    (is (= 5 (count result-set)))
    (is (every? :invoice/id result-set))
    ;(dev/pp result-set)
    ))

(deftest customer-on-an-invoice
  (let [env (env)
        {::kv-key-store/keys [table->ident-rows]} (:main kv-connections)
        iident (my-rand-nth (table->ident-rows :invoice/id))
        cid (queries/get-invoice-customer-id env (second iident))]
    (is (uuid? cid))))

(deftest category-of-a-line-item
  (let [env (env)
        {::kv-key-store/keys [table->ident-rows]} (:main kv-connections)
        li-ident (my-rand-nth (table->ident-rows :line-item/id))
        cid (queries/get-line-item-category env (second li-ident))]
    (is (uuid? cid))))

;; get-login-info
(deftest get-known-account
  (let [env (env)
        {:account/keys [id name email] :as account} (queries/get-login-info env "sam@example.com")]
    (is (= id (new-uuid 101)))
    (is (= name "Sam"))
    (is (= email "sam@example.com"))
    ;(dev/pp account)
    ))

;;
;; It is true there's no category at 1001, in my data anyway
;;
(deftest all-categories
  (let [expected-set #{{:category/id (new-uuid 1000)} {:category/id (new-uuid 1002)} {:category/id (new-uuid 1003)}}
        result-set (set (queries/get-all-categories (env) {}))]
    (is (= (set expected-set) result-set))
    ;(dev/pp result-set)
    ))
