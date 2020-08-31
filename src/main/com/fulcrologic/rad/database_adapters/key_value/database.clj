(ns com.fulcrologic.rad.database-adapters.key-value.database
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.database-adapters.key-value.adaptor :as kv-adaptor]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]))

;;
;; Later we can export to an edn file then import back in
;;
(>defn export
  "Sometimes useful to see the whole database at once"
  [db env tables]
  [::kv-adaptor/key-store map? ::key-value/tables => vector?]
  (into {} (for [table tables]
             (let [entities (->> (kv-adaptor/read-table db env table)
                                 (mapv (fn [m]
                                         (let [ident [table (get m table)]]
                                           (kv-adaptor/read1 db env ident)))))]
               [table entities]))))
