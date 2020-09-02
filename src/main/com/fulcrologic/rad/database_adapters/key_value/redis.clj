(ns com.fulcrologic.rad.database-adapters.key-value.redis
  "An implementation of `::kv-adaptor/KeyStore` for Redis"
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [swap!->]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [taoensso.carmine :as car]
    [taoensso.timbre :as log]))

;;
;; T
;; TODO: We should figure out how to make each of these "drivers" optional so we don't explode ppls deps as we
;; add new ones.  Perhaps generate multiple jars for clojars from this one project, or use dyn ns resolution?
;;
;; C
;; Or a separate library for each, multiple projects. So this current deps.edn project to just contain KeyStore and
;; MemoryKeyStore. Hive off RedisKeyStore into another deps.edn project. Whilst Redis might be small currently the
;; others will be a lot more work (as will need equivalent of Nippy to be part of them right??)
;;
;; Or as you say multiple alias, one each to generate a jar and go to different maven artifacts. An earlier version
;; this project was using seancorfield/depstar (which can be passed an explicit classpath (alias)) and
;; slipset/deps-deploy for deployment to Clojars.
;; Example of generating the jar file from the command line, and picking up the classpath from the `:redis` alias:
;;
;; clojure -A:depstar:redis -m hf.depstar.jar fulcro-rad-kvstore-redis.jar
;;

(>defn upsert-new-value!
  "Read the old value and merge over it with the new. If `table-kludge?` is on then the list of all row ids for that table
  is updated. We do this even if it is an existing row that is being changed"
  [conn table-kludge? [table id :as ident] m]
  [map? boolean? ::key-value/ident map? => any?]
  (let [last-value (or (car/wcar conn (car/get ident)) {})]
    (car/wcar conn (car/set ident (merge last-value m)))
    (when table-kludge?
      (let [row-ids (car/wcar conn (car/get table))
            to-store (if row-ids
                       (conj row-ids id)
                       #{id})]
        (car/wcar conn (car/set table to-store))))))

(>defn remove-row
  "Just set the ident to nil. Remove from row-ids of that table when `:key-value/table-kludge?` is on"
  [conn table-kludge? [table id :as ident]]
  [map? boolean? ::key-value/ident => any?]
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

(defn- write!
  [conn {:key-value/keys [table-kludge?]} pairs-of-ident-map]
  (let [entries (cond
                  ((every-pred seq (complement map?)) pairs-of-ident-map) pairs-of-ident-map
                  (map? pairs-of-ident-map) (into [] pairs-of-ident-map))]
    (doseq [entry entries]
      (feed-pair! conn table-kludge? entry))))

(deftype RedisKeyStore [conn options] kv-adaptor/KeyStore
  (-read1 [this env ident]
    (car/wcar conn (car/get ident)))
  (-read* [this env idents]
    (car/wcar conn (mapv (fn [ident] (car/get ident)) idents)))
  (-read-table [this env table]
    (if (not (:key-value/table-kludge? options))
      (do
        (log/error "table" table "cannot be queried with `:key-value/table-kludge?` set to" (:key-value/table-kludge? options))
        [])
      (mapv (fn [id] [table id]) (car/wcar conn (car/get table)))))
  (-write* [this env pairs-of-ident-map]
    (write! conn options pairs-of-ident-map))
  (-write1 [this env ident m]
    (write! conn options [[ident m]]))
  (-remove1 [this env ident]
    (remove-row conn (:key-value/table-kludge? options) ident))
  (-instance-name [this]
    (-> conn :spec :uri))
  (-options [this]
    options))