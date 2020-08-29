(ns play.com.fulcrologic.rad.database-adapters.key-value.write
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.write :as key-value-write]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]))

(defn x-1 []
  [(key-value-write/before-after? {:before :a :after :b})
   (key-value-write/before-after? {:before :a :afterwards :b})])

;
;([[:line-item/id 1] #:line-item{:id 1, :hash 7}]
; [[:line-item/id 2] #:line-item{:id 2, :hash 5}]
; [[:invoice/id 1] #:invoice{:id 1, :line-items [#:line-item{:id 1, :hash 7} #:line-item{:id 2, :hash 5}]}])
;
(defn test-flatten []
  (let [m {:invoice/id         1
           :invoice/line-items [{:line-item/id 1 :line-item/hash 7}
                                {:line-item/id 2 :line-item/hash 5}]}]
    (key-value-write/flatten m)))