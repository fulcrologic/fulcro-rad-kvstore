(ns play.com.fulcrologic.rad.database-adapters.key-value.redis
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.redis :as redis-adaptor]
    [taoensso.carmine :as car :refer (wcar)]
    [com.fulcrologic.rad.database-adapters.key-value.read :as key-value-read]
    [general.dev :as dev]
    [com.fulcrologic.rad.database-adapters.key-value.write :as key-value-write]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.ids :refer [new-uuid]]))

(def uri "redis://127.0.0.1:6379/")
(def server1-conn {:pool {} :spec {:uri uri}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

(defn x-1 []
  (car/wcar server1-conn (car/ping)))

(defn x-2 []
  (car/wcar server1-conn (car/set [:some-table 2] {:some-table/id       2
                                                   :some-table/greeting "Hi"})))

(defn x-3 []
  (car/wcar server1-conn (car/get [:some-table 2])))

(def pathom-env {})

(def account-ident [:account/id ["455e5d86-fe34-48cb-9fbc-6127bcbe1117"]])
(def address-ident [:address/id ["98e35faf-b7cc-4702-b6bd-199260fdc68e"]])

(def incoming {account-ident
               {:account/id              ["455e5d86-fe34-48cb-9fbc-6127bcbe1117"]
                :account/name            "Chris",
                :account/role            :account.role/user,
                :time-zone/zone-id       nil,
                :account/email           "chris@seasoft.com.au",
                :account/active?         true,
                :account/primary-address address-ident,
                :account/addresses       [],
                :account/files           []},
               address-ident
               {:address/street "1 Philip", :address/city "Sydney", :address/state :address.state/CT, :address/zip "2345"}})

;;
;; Test out any new adaptor like this...
;;
(def m {:some-table/id       5
        :some-table/greeting "Hallo Sailor"})

(def adaptor (redis-adaptor/->RedisKeyStore server1-conn true))

;;
;; Doesn't work so simplify
;;
(defn write-first []
  (key-value-write/write-tree adaptor pathom-env (key-value-read/map->ident m) m))

;; Works like this
(defn write-simply []
  (car/wcar server1-conn (car/set (key-value-read/map->ident m) m)))

(defn then-read-from-outer []
  (dev/log-on "reading the row" (key-value-read/read-tree adaptor pathom-env (key-value-read/map->ident m))))

(defn then-read-from-inner []
  (dev/log-on "reading the row" (dev/pp-str (kv-adaptor/read* adaptor pathom-env (key-value-read/map->ident m)))))

;;
;; Needed to remove duplicates
;;
(defn test-flatten-1 []
  (key-value-write/flatten m))

;;
;; Now simplify by writing two doing doseq and see if they there
;;
(defn write-2 []
  (doseq [[id m] incoming]
    (dev/log-on id m)
    (car/wcar server1-conn (car/set id m))))

(defn read-them []
  (dev/log-on (car/wcar server1-conn (car/get account-ident)))
  (dev/log-on (car/wcar server1-conn (car/get address-ident)))
  (dev/log-on (car/wcar server1-conn (car/get [:some-table 2]))))

(defn write-account []
  (car/wcar server1-conn (car/set :account/id (into #{} [(new-uuid 100)]))))

(defn get-account []
  (dev/log-on (car/wcar server1-conn (car/get :account/id))))

(defn append-another []
  (car/wcar server1-conn (redis-adaptor/upsert-new-value
                           true
                           server1-conn
                           [:account/id (new-uuid 101)]
                           {:account/id (new-uuid 101)})))


