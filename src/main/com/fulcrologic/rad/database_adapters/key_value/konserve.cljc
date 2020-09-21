(ns com.fulcrologic.rad.database-adapters.key-value.konserve
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.database-adapters.strict-entity :as strict-entity]
    [konserve.core :as k]
    [konserve.memory :refer [new-mem-store]]
    #?(:cljs [konserve.indexeddb :refer [new-indexeddb-store]])
    #?(:clj [konserve.filestore :refer [new-fs-store]])
    #?(:clj [clojure.core.async :as async :refer [<!! <! >! close! chan go go-loop]])
    #?(:cljs [cljs.core.async :as async :refer [<! >! close! chan go go-loop]])
    [taoensso.timbre :as log]))

(defmethod kv-key-store/make-adaptor
  :memory
  [_ _]
  [(str "Konserve memory store")
   (new-mem-store)])

#?(:clj
   (defmethod kv-key-store/make-adaptor
     :filestore
     [_ {:filestore/keys [location] :as options}]
     [(str "Konserve file store at " location)
      (new-fs-store location)]))

#?(:cljs
   (defmethod kv-key-store/make-adaptor
     :indexeddb
     [_ {:indexeddb/keys [name] :as options}]
     [(str "Konserve IndexedDB store called " name)
      (new-indexeddb-store name)]))

;;
;; There are supposed to be bang versions
;; https://github.com/replikativ/konserve/issues/24
;; So not need to <!! here every time
;; Not documented in Konserve apart from a resolved issue.
;;
;; These functions are inside a ::key-value/key-store. That's why they are special and take a ::kv-key-store/store
;; as an arg. Normally always pass around a ::key-value/key-store and de-structure to a store or one of these
;; functions
;;

(>defn table->rows
  "Get all the data for a particular table as a vector of maps. Returns a chan"
  [store table]
  [::kv-key-store/store ::strict-entity/table => any?]
  (go
    (vec (vals (<! (k/get-in store [table]))))))

(>defn table->ident-rows
  "Instead of getting all the data, get all the rows but only in ident form. Returns a chan"
  [store table]
  [::kv-key-store/store ::strict-entity/table => any?]
  (go (->> (keys (<! (k/get-in store [table])))
           (mapv (fn [id] [table id])))))

(>defn ident->entity
  "Given an ident, fetch out a whole entity. Returns a chan"
  [store ident]
  [::kv-key-store/store ::strict-entity/ident => any?]
  (k/get-in store ident))

(defn remove-table-rows
  "Remove all the rows, so all the data, from a particular table"
  [store table]
  (k/dissoc store table))

(>defn write-entity
  "Store an entity at its ident location. Returns a chan"
  [store entity]
  [::kv-key-store/store ::strict-entity/entity => any?]
  (k/assoc-in store (strict-entity/entity->ident entity) entity))

(>defn ids->entities
  "Getting a load of entities from idents at once (async) and putting them into an output chan. Returns a chan"
  [store table ids]
  [::kv-key-store/store ::strict-entity/table (s/coll-of ::strict-entity/id) => any?]
  (let [out (chan (count ids))]
    (go
      (loop [ids ids]
        (when-let [id (first ids)]
          (let [entity (<! (k/get-in store [table id]))]
            (if entity
              (>! out entity)
              (log/warn "Could not find an entity from ident" [table id]))
            (recur (next ids)))))
      (close! out))
    (async/into [] out)))

(defn write-entities
  "Overwrite current value of idents. Each entry to process is of type [ident entity]"
  [store entries]
  (go-loop [entries entries]
    (when-let [[ident m] (first entries)]
      (k/assoc-in store ident m)
      (recur (rest entries)))))

(defn merge-entities
  "Same as `write-entities` but existing attributes are left if there is no new value for them"
  [store pairs]
  (go-loop
    [[pair & more] pairs]
    (when-let [[ident m] (if (map? pair)
                           (first pair)
                           pair)]
      (k/update-in store ident merge m)
      (recur more))))