(ns play.konserve.chan-only-2
  (:require
    [play.konserve.entity-read :as entity-read]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.fulcro.server.config :as fserver]
    [clojure.core.async :as async :refer [<!! <! chan go go-loop]]
    [konserve.core :as k]
    [au.com.seasoft.general.dev :as dev]
    [taoensso.timbre :as log]))

(def config (fserver/load-config! {:config-path "config/dev.edn"}))
(def msg "Atom is empty")

;;
;; A place to put the store when we access it once
;;
(defonce our-store (atom nil))

;;
;; In reality this might be a mutation into Fulcro app state. For that reason there's no such thing as a `get-f`.
;; 'our-store' here is a ::key-value/key-store that will be available globally to all components so they can easily
;; grab data from it (and also put data in!). This will only be called once, at startup time.
;;
(>defn put-f [key-store]
  [::key-value/key-store => any?]
  (reset! our-store key-store))

;;
;; 1
;;
;; Access the store once and save it into Fulcro app state (or shared state or whatever)
;; With chan-only-1 we were putting in only the adaptor. Here we are putting in the key-store
;;
(defn put-store-into-atom []
  (key-value/start-async config put-f))

;;
;; 2
;;
;; Save an entity to our store
;;
(defn save-into-store-1 []
  (go
    (let [{::kv-key-store/keys [store]} @our-store]
      (if store
        (<! (k/assoc-in store [:person/id (new-uuid 1)] entity-read/test-entity-1))
        (log/error msg @our-store)))))

;;
;; 3
;;
;; Retrieve it back again
;;
(defn get-store-contents-1 []
  (go
    (let [{::kv-key-store/keys [store]} @our-store]
      (if store
        (dev/log-on "See\n" (dev/pp-str (<! (k/get-in store [:person/id (new-uuid 1)]))))
        (log/error msg @our-store)))))

;;
;; 4
;;
;; Retrieve it back again using a standard method
;;
(defn get-store-contents-2 []
  (go
    (let [{::kv-key-store/keys [ident->entity]} @our-store]
      (if ident->entity
        (dev/log-on "See\n" (dev/pp-str (<! (ident->entity [:person/id (new-uuid 1)]))))
        (log/error msg @our-store)))))

;;
;; 5
;;
;; Save another entity, this time using a standard method
;;
(defn save-into-store-2 []
  (go
    (let [{::kv-key-store/keys [write-entity]} @our-store]
      (if write-entity
        (<! (write-entity entity-read/test-entity-2))
        (log/error msg @our-store)))))

;;
;; 6
;;
;; Check that either one is there (call multiple times)
;;
(defn get-store-contents-2 []
  (go
    (let [{::kv-key-store/keys [ident->entity]} @our-store
          random-ident (rand-nth [[:person/id (new-uuid 1)][:person/id (new-uuid 2)]])]
      (if ident->entity
        (dev/log-on "See\n" (dev/pp-str (<! (ident->entity random-ident))))
        (log/error msg @our-store)))))
