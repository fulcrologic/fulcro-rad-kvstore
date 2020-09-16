(ns play.konserve.entity-read
  (:require
    [taoensso.timbre :as log]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [edn-query-language.core :as eql]
    [au.com.seasoft.general.dev :as dev]
    [konserve.filestore :refer [new-fs-store]]
    [konserve.core :as k]
    [clojure.core.async :as async :refer [<!! chan go go-loop]]))

(defn entity->idents [m]
  (reduce
    (fn [acc [k v]]
      (cond
        (eql/ident? v) (conj acc v)
        (vector? v) (concat acc v)
        :else acc))
    []
    m))

(def test-entity-3
  {:person/id            (new-uuid 3)
   :person/first-name    "Three"
   :person/surname       "Three"})
(def test-entity-4
  {:person/id            (new-uuid 4)
   :person/first-name    "Four"
   :person/surname       "Four"})
(def test-entity-7
  {:person/id            (new-uuid 7)
   :person/first-name    "Seven"
   :person/surname       "Seven"})
(def test-entity-8
  {:person/id            (new-uuid 8)
   :person/first-name    "Eight"
   :person/surname       "Eight"})

(def test-entity-2
  {:person/id            (new-uuid 2)
   :person/first-name    "Nick"
   :person/surname       "Cage"
   :person/best-friend   [:person/id (new-uuid 1)]
   :person/enemies       [[:person/id (new-uuid 3)]]
   :person/other-friends [[:person/id (new-uuid 7)] [:person/id (new-uuid 8)]]
   })

(def test-entity-1
  {:person/id            (new-uuid 1)
   :person/first-name    "Sandra"
   :person/surname       "Sully"
   :person/best-friend   [:person/id (new-uuid 2)]
   :person/enemies       [[:person/id (new-uuid 3)] [:person/id (new-uuid 4)]]
   :person/other-friends [[:person/id (new-uuid 7)] [:person/id (new-uuid 8)]]
   })

(defn x-1 []
  (dev/pp (entity->idents test-entity-1)))

(def store-1 (<!! (new-fs-store "/tmp/store")))
(def store-2 (<!! (new-fs-store "/tmp/fulcro_rad_demo_db")))

;; This shows all the others can become a lot more like an atom
;; (k/get-in store [:person/id]) brings back the whole table, so no hacks needed...
(defn write-to-file []
  (<!! (k/assoc-in store-1 [:person/id (new-uuid 1)] test-entity-1))
  (<!! (k/assoc-in store-1 [:person/id (new-uuid 2)] test-entity-2))
  (<!! (k/assoc-in store-1 [:person/id (new-uuid 3)] test-entity-3))
  (<!! (k/assoc-in store-1 [:person/id (new-uuid 4)] test-entity-4))
  (<!! (k/assoc-in store-1 [:person/id (new-uuid 7)] test-entity-7))
  (<!! (k/assoc-in store-1 [:person/id (new-uuid 8)] test-entity-8)))

(defn read-back-1 []
  (dev/pp (<!! (k/get-in store-1 [:person/id (new-uuid 1)])))
  ;; [:person/id] brings back the whole table (just like an app db (Fulcro) table)
  (dev/pp (keys (<!! (k/get-in store-1 [:person/id])))))

(defn read-back-2 []
  (dev/pp (keys (<!! (k/get-in store-2 [:account/id #_(new-uuid 101)])))))

(defn purge-table []
  (<!! (k/dissoc store-1 :person/id)))

;;
;; Always put [ident {}] into the output channel
;; Will finish up with a map of all of them
;; So rest is outside core.async...
;; Know the first ident, so can keep looking up from it and build up the tree via recursion
;; But ideally it would be user code that does this. Haha - that's get-in
;; Will be more manual code w/out a deep entity-read
;; So an entity-read is useful but not essential
;; We can probably get the application going without it
;;
(defn entity->idents-ch [ch entity]
  (let [idents (entity->idents entity)]
    (go-loop [idents idents]
      (let [ident (first idents)
            m (k/get-in store-1 ident)]
        ))))

(defn x-3 []
  )
