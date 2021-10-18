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
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [cljs.core.async :refer [<! go]]))

;;
;; TODO
;; In RAD how does the client usually pick up config? So find out and use that way rather than this way
;;
(def config #::key-value{:databases {:main {:key-value/kind :indexeddb
                                            :indexeddb/name "Customer Invoices DB"}}})

(defonce key-store-atom (atom nil))

(defn my-rand-nth [xs]
  (if (empty? xs)
    nil
    (rand-nth xs)))

(defsc LandingPage [this props]
  {:query         ['*]
   :ident         (fn [] [:component/id ::LandingPage])
   :initial-state {}
   :route-segment ["landing-page"]}
  (let [put-f (fn [key-store]
                (reset! key-store-atom key-store)
                (println "Saved" (kv-key-store/display key-store) "away"))]
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
      (dom/br)
      (dom/button
        {:onClick (fn []
                    (go
                      (let [res (<! (queries/get-all-accounts @key-store-atom {:show-inactive? true}))]
                        (println res))))
         :style {:cursor "pointer"}}
        "All accounts query")
      (dom/br)
      (dom/button
        {:onClick (fn []
                    (go
                      (let [res (<! (queries/get-all-items @key-store-atom {:category/id (new-uuid 1000)}))]
                        (println res))))
         :style {:cursor "pointer"}}
        "Query of all items of category 1000")
      (dom/br)
      (dom/button
        {:onClick (fn []
                    (go
                      (let [res (<! (queries/get-customer-invoices @key-store-atom {:account/id (new-uuid 102)}))]
                        (println res))))
         :style {:cursor "pointer"}}
        "Customer invoices of account 102")
      (dom/br)
      (dom/button
        {:onClick (fn []
                    (go
                      (let [res (<! (queries/get-all-invoices @key-store-atom))]
                        (println res))))
         :style {:cursor "pointer"}}
        "All invoices")
      (dom/br)
      (dom/button
        {:onClick (fn []
                    (go
                      (let [res (<! (queries/get-all-invoices @key-store-atom))]
                        (println res))))
         :style {:cursor "pointer"}}
        "Customer on an invoice")
      (dom/br)
      (dom/button
        {:onClick (fn []
                    (go
                      (let [{::kv-key-store/keys [table->ident-rows]} @key-store-atom
                            li-ident (my-rand-nth (<! (table->ident-rows :line-item/id)))
                            _ (println "Supposed to be ident, getting" li-ident)
                            cid (<! (queries/get-line-item-category @key-store-atom (second li-ident)))]
                        (println cid))))
         :style   {:cursor "pointer"}}
        "Category of a random line item")
      (dom/br)
      (dom/button
        {:onClick (fn []
                    (go
                      (let [{:account/keys [id name email] :as account} (<! (queries/get-login-info @key-store-atom "sam@example.com"))]
                        (println account))))
         :style   {:cursor "pointer"}}
        "Sam's account")
      (dom/br)
      (dom/button
        {:onClick (fn []
                    (go
                      (let [categories (<! (queries/get-all-categories @key-store-atom))]
                        (println categories))))
         :style   {:cursor "pointer"}}
        "All categories"))))
