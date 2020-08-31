(ns com.fulcrologic.rad.database-adapters.key-value.redis
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [taoensso.carmine :as car]
    [taoensso.timbre :as log]
    [edn-query-language.core :as eql]))

;;
;; T
;; TODO: We should figure out how to make each of these "drivers" optional so we don't explode ppls deps as we
;; add new ones.  Perhaps generate multiple jars for clojars from this one project, or use dyn ns resolution?
;; C
;; Or a separate library for each. So this current deps.edn project to just contain KeyStore and MemoryKeyStore.
;; Hive off RedisKeyStore into another deps.edn project. Thus Room for expansion in each. Whilst Redis might be
;; small the others will be a lot more work (as will need equivalent of Nippy to be part of them right??)
;;

(>defn upsert-new-value! [conn table-kludge? [table id :as ident] m]
  [map? boolean? eql/ident? map? => any?]
  (let [last-value (or (car/wcar conn (car/get ident)) {})]
    (car/wcar conn (car/set ident (merge last-value m)))
    (when table-kludge?
      (let [row-ids (car/wcar conn (car/get table))
            to-store (if row-ids
                       (conj row-ids id)
                       #{id})]
        (car/wcar conn (car/set table to-store))))))

(>defn remove-row [conn table-kludge? [table id :as ident]]
  [map? boolean? eql/ident? => any?]
  (car/wcar conn (car/set ident nil))
  (when table-kludge?
    (let [row-ids (car/wcar conn (car/get table))
          to-store (disj row-ids id)]
      (car/wcar conn (car/set table to-store)))))

(defn- feed-pair! [conn table-kludge? pair]
  (let [[ident m] (if (map? pair)
                    (first pair)
                    pair)]
    (upsert-new-value! conn table-kludge? ident m)))

(defn- inner-write!
  [conn table-kludge? pairs-of-ident-map]
  (let [entries (cond
                  ((every-pred seq (complement map?)) pairs-of-ident-map) pairs-of-ident-map
                  (map? pairs-of-ident-map) (into [] pairs-of-ident-map))]
    (doseq [entry entries]
      (feed-pair! conn table-kludge? entry))))

;;
;; read-outer allows you to get a completely denormalized tree from an ident
;; See read-tree
;; However we still preference EQL/Pathom by always returning a map.
;; When table-kludge? is true we have an idea of tables so that they can be queried.
;; But this comes at a performance cost.
;;
(deftype RedisKeyStore [conn table-kludge?] kv-adaptor/KeyStore
  (-instance-name-f [this] (-> conn :spec :uri))
  (-read1 [this env ident]
    (car/wcar conn (car/get ident)))
  (-read* [this env idents]
    (car/wcar conn (mapv (fn [ident] (car/get ident)) idents)))
  (-read-table [this env table]
    (if (not table-kludge?)
      (do
        (log/error "table" table "cannot be queried with table-kludge? set to false")
        {})
      (->> (car/wcar conn (car/get table))
           (mapv (fn [id] {table id})))))
  (-write* [this env pairs-of-ident-map]
    (inner-write! conn table-kludge? pairs-of-ident-map))
  (-write1 [this env ident m]
    (inner-write! conn table-kludge? [[ident m]]))
  (-remove1 [this env ident]
    (remove-row conn table-kludge? ident)))