(ns com.fulcrologic.rad.database-adapters.key-value.redis
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [taoensso.carmine :as car]
    [taoensso.timbre :as log]))

(defn upsert-new-value [conn table-kludge? [table id :as ident] m]
  (assert (qualified-keyword? table))
  (assert (uuid? id))
  (let [last-value (or (car/wcar conn (car/get ident)) {})]
    (car/wcar conn (car/set ident (merge last-value m)))
    (when table-kludge?
      (let [row-ids (car/wcar conn (car/get table))
            _ (assert ((some-fn nil? set?) row-ids) ["Not a set as expected" (type row-ids)])
            to-store (if row-ids
                       (conj row-ids id)
                       #{id})]
        (car/wcar conn (car/set table to-store))))))

(defn remove-row [conn table-kludge? [table id :as ident]]
  (assert (qualified-keyword? table))
  (assert (uuid? id))
  (car/wcar conn (car/set ident nil))
  (when table-kludge?
    (let [row-ids (car/wcar conn (car/get table))
          _ (assert ((some-fn nil? set?) row-ids) ["Not a set as expected" (type row-ids)])
          to-store (disj row-ids id)]
      (car/wcar conn (car/set table to-store)))))

(defn feed-pair [conn table-kludge? pair]
  (let [[ident m] (if (map? pair)
                    (do
                      (assert (= 1 (count pair)) ["Expected only one map-entry" pair])
                      (first pair))
                    pair)]
    (upsert-new-value conn table-kludge? ident m)))

(defn- inner-write
  [conn table-kludge? pairs-of-ident-map]
  (let [entries (cond
                  ((every-pred seq (complement map?)) pairs-of-ident-map) pairs-of-ident-map
                  (map? pairs-of-ident-map) (into [] pairs-of-ident-map))]
    (doseq [entry entries]
      (feed-pair conn table-kludge? entry))))

;;
;; read-outer allows you to get a completely denormalized tree from an ident
;; See read-tree
;; However we still preference EQL/Pathom by always returning a map.
;; When table-kludge? is true we have an idea of tables so that they can be queried.
;; But this comes at a performance cost.
;;
(deftype RedisKeyStore [conn table-kludge?] kv-adaptor/KeyStore
  (db-f [this env] conn)
  (instance-name-f [this env] (-> conn :spec :uri))
  (read* [this env ident-or-idents-or-table]
    (let [cardinality (kv-adaptor/cardinality ident-or-idents-or-table)]
      (case cardinality
        :ident (car/wcar conn (car/get ident-or-idents-or-table))
        :keyword (if (not table-kludge?)
                   (do
                     (log/error "table" ident-or-idents-or-table "cannot be queried with table-kludge? set to false")
                     {})
                   (->> (car/wcar conn (car/get ident-or-idents-or-table))
                        (mapv (fn [id] {ident-or-idents-or-table id}))))
        :idents (car/wcar conn (mapv (fn [ident] (car/get ident)) ident-or-idents-or-table)))))
  (write* [this env pairs-of-ident-map]
    (inner-write conn table-kludge? pairs-of-ident-map))
  (write1 [this env ident m]
    (inner-write conn table-kludge? [[ident m]]))
  (remove1 [this env ident]
    (remove-row conn table-kludge? ident)))