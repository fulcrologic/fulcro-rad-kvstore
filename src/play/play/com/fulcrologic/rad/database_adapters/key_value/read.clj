(ns play.com.fulcrologic.rad.database-adapters.key-value.read
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.read :as key-value-read]))

(defn x-4 []
  (key-value-read/map->eql-result {:person/id     1
                                   :db/id         7
                                   :person/alive? true
                                   :person/pulse  65}
                                  :person/id))