(ns test.com.fulcrologic.rad.database-adapters.key-value.write-test
  (:require [clojure.test :refer :all]
            [com.fulcrologic.rad.ids :refer [new-uuid]]
            [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]))

(deftest before-after-good
  (is (true? (kv-write/before-after? {:before :a :after :b}))))

(deftest before-after-bad
  (is (false? (kv-write/before-after? {:before :a :afterwards :b}))))

(deftest test-flatten
  (let [expected [[[:line-item/id (new-uuid 1)] #:line-item{:id (new-uuid 1), :hash 7}]
                  [[:line-item/id (new-uuid 2)] #:line-item{:id (new-uuid 2), :hash 5}]
                  [[:invoice/id (new-uuid 1)] #:invoice{:id         (new-uuid 1),
                                                        :line-items [[:line-item/id (new-uuid 1)] [:line-item/id (new-uuid 2)]]}]]
        m {:invoice/id         (new-uuid 1)
           :invoice/line-items [{:line-item/id (new-uuid 1) :line-item/hash 7}
                                {:line-item/id (new-uuid 2) :line-item/hash 5}]}
        res (kv-write/flatten m)]
    (is (= expected res))))
