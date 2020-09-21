(ns com.example.ui.landing-page-2
  "Enhanced original landing page. So original code can easily use this instead. In cljs b/c we never want to be
  running it on the JVM. We never want to be running it on the JVM b/c we are testing a browser based database"
  (:require
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div label input]]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.example.components.seed :as seed]
    [com.example.components.queries :as queries]
    [cljs.core.async :refer [<! go]]
    [au.com.seasoft.general.dev :as dev]))

;;
;; TODO
;; In RAD how does the client usually pick up config? So find out and use that way rather than this way
;;
(def config #::key-value{:databases {:main {:key-value/kind :indexeddb
                                            :indexeddb/name "Customer Invoices DB"}}})

(def key-store-atom (atom nil))

(defsc LandingPage [this props]
       {:query         ['*]
        :ident         (fn [] [:component/id ::LandingPage])
        :initial-state {}
        :route-segment ["landing-page"]}
       (let [put-f (fn [key-store]
                     (println "Need to save" (kv-key-store/display key-store) "away")
                     (reset! key-store-atom key-store))]
         (dom/div
           (dom/div "Welcome to the Demo. Please log in.")
           (dom/br)
           (dom/br)
           (dom/h4 "Testing Key Value store client-side DB")
           (dom/div "Lets test out IndexedDB. See browser console for results")
           (dom/br)
           (dom/button
             {:onClick (fn []
                         (key-value/start-async config put-f))
              :style {:cursor "pointer"}}
             "Create key store")
           (dom/br)
           (dom/button
             {:onClick (fn []
                         (seed/seed! @key-store-atom))
              :style {:cursor "pointer"}
              }
             "Seed new entities")
           (dom/br)
           (dom/button
             {:onClick (fn []
                         (go
                           (let [res (<! (queries/get-all-accounts @key-store-atom {:show-inactive? true}))]
                             (dev/pp res))))
              :style {:cursor "pointer"}}
             "Do a query")
           )))

