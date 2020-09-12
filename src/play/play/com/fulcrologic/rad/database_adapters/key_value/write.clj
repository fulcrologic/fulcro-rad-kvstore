(ns play.com.fulcrologic.rad.database-adapters.key-value.write
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]))

;;
;; TODO
;; Turn into tests and get rid of from here
;; Can get rid of the play queries at same time (as have tests and can run them individually in REPL)
;;

(defn x-1 []
  [(kv-write/before-after? {:before :a :after :b})
   (kv-write/before-after? {:before :a :afterwards :b})])

;
;([[:line-item/id 1] #:line-item{:id 1, :hash 7}]
; [[:line-item/id 2] #:line-item{:id 2, :hash 5}]
; [[:invoice/id 1] #:invoice{:id 1, :line-items [#:line-item{:id 1, :hash 7} #:line-item{:id 2, :hash 5}]}])
;
(defn test-flatten []
  (let [m {:invoice/id         1
           :invoice/line-items [{:line-item/id 1 :line-item/hash 7}
                                {:line-item/id 2 :line-item/hash 5}]}]
    (kv-write/flatten m)))