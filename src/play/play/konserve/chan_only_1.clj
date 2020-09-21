(ns play.konserve.chan-only-1
  (:require
    [play.konserve.entity-read :as entity-read]
    [com.example.components.config :as config]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.fulcro.server.config :as fserver]
    [clojure.core.async :as async :refer [<!! <! chan go go-loop]]
    [konserve.core :as k]
    [au.com.seasoft.general.dev :as dev]))

(def config (fserver/load-config! {:config-path "config/dev.edn"}))

;;
;; B/c it is a channel and we are gonna get something out of it, can only access the store once
;;
(defn make-store []
  (let [{::kv-key-store/keys [store]} (key-value/start config)]
    store))

;;
;; A place to put the store when we access it once
;;
(def our-store (atom nil))

;;
;; 1
;;
;; Access the store once and save it into Fulcro app state (or shared state or whatever)
;;
(defn put-store-in-atom []
  (go
    (reset! our-store (<! (make-store)))))

;;
;; 2
;;
;; Save an entity to our store
;;
(defn save-into-store []
  (go
    (<! (k/assoc-in @our-store [:person/id (new-uuid 1)] entity-read/test-entity-1))))

;;
;; 3
;;
;; Retrieve it back again
;;
(defn get-store-contents []
  (go (println "See\n" (dev/pp-str (<! (k/get-in @our-store [:person/id (new-uuid 1)]))))))