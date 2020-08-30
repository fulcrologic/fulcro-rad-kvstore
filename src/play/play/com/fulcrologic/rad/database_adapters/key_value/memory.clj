(ns play.com.fulcrologic.rad.database-adapters.key-value.memory
  (:require
    [com.example.components.seeded-connection :refer [all-tables!]]
    [com.fulcrologic.rad.database-adapters.key-value.memory :as memory-adaptor]
    [general.dev :as dev]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write :refer [value-of ident-of]]
    [com.fulcrologic.rad.database-adapters.key-value.read :as kv-read]
    [com.fulcrologic.rad.ids :refer [new-uuid]]))

(def pathom-env {})

(def incoming {[:account/id ["455e5d86-fe34-48cb-9fbc-6127bcbe1117"]]
               {:account/name            "Chris",
                :account/role            :account.role/user,
                :time-zone/zone-id       nil,
                :account/email           "chris@seasoft.com.au",
                :account/active?         true,
                :account/primary-address [:address/id ["98e35faf-b7cc-4702-b6bd-199260fdc68e"]],
                :account/addresses       [],
                :account/files           []},
               [:address/id ["98e35faf-b7cc-4702-b6bd-199260fdc68e"]]
               {:address/street "1 Philip", :address/city "Sydney", :address/state :address.state/CT, :address/zip "2345"}})

(defn x-1 []
  (let [feed-pair (partial memory-adaptor/feed-pair pathom-env)]
    (dev/pp (reduce
              feed-pair
              {}
              incoming))))

;;
;; Test out any new adaptor like this...
;;

(defn write-and-read-1 []
  (let [ks (memory-adaptor/->MemoryKeyStore "x-1" (atom {}))]
    (kv-write/write-tree ks pathom-env [:some-table/id 1] {:some-table/id 1
                                                                  :some-table/greeting "Hi"})
    (dev/log-on "reading the row" (kv-read/read-tree ks pathom-env [:some-table/id 1]))))

(defn greg []
  {:person/id   1
   :person/name "Greg"})

(defn sally []
  {:person/id   2
   :person/name "Sally"})

(defn write-and-read-2 []
  (let [ks (memory-adaptor/->MemoryKeyStore "x-2" (atom {}))]
    (dev/log-on "Whole DB just written:")
    (kv-write/write-tree ks pathom-env [:some-table/id 1] {:some-table/id        1
                                                                  :some-table/greeting  "Hi"
                                                                  :some-table/leg-count 3
                                                                  :some-table/sitters   [(greg) (sally)]
                                                                  :some-table/finish    {:finish/id       4
                                                                                         :finish/battered true
                                                                                         :finish/colour   "red"}})
    (doseq [table (all-tables!)]
      (dev/pp (kv-adaptor/read* ks pathom-env table)))
    (dev/log-on "Individual reads")
    ;(dev/pp (read* db env [:person/id 2] true))
    (kv-read/read-compact ks pathom-env [:some-table/id 1])))
