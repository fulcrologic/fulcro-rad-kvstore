(ns com.example.ui
  (:require
    #?@(:cljs [[com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
               [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-menu :refer [ui-dropdown-menu]]
               [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-item :refer [ui-dropdown-item]]])
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom :refer [div label input]]
       :cljs [com.fulcrologic.fulcro.dom :as dom :refer [div label input]])
    [com.example.ui.account-forms :refer [AccountForm AccountList]]
    [com.example.ui.invoice-forms :refer [InvoiceForm InvoiceList AccountInvoices]]
    [com.example.ui.item-forms :refer [ItemForm InventoryReport]]
    [com.example.ui.line-item-forms :refer [LineItemForm]]
    [com.example.ui.sales-report :as sales-report]
    [com.example.ui.dashboard :as dashboard]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :refer [defrouter]]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.routing :as rroute]
    [taoensso.timbre :as log]))

(defsc LandingPage [this props]
  {:query         ['*]
   :ident         (fn [] [:component/id ::LandingPage])
   :initial-state {}
   :route-segment ["landing-page"]}
  (dom/div "Welcome to the Demo."))

;; This will just be a normal router...but there can be many of them.
(defrouter MainRouter [this {:keys [current-state route-factory route-props]}]
  {:always-render-body? true
   :router-targets      [LandingPage ItemForm InvoiceForm InvoiceList AccountList AccountForm AccountInvoices
                         sales-report/SalesReport InventoryReport
                         sales-report/RealSalesReport
                         dashboard/Dashboard]}
  ;; Normal Fulcro code to show a loader on slow route change (assuming Semantic UI here, should
  ;; be generalized for RAD so UI-specific code isn't necessary)
  (dom/div
    (dom/div :.ui.loader {:classes [(when-not (= :routed current-state) "active")]})
    (when route-factory
      (route-factory route-props))))

(def ui-main-router (comp/factory MainRouter))

(defsc Root [this {::app/keys [active-remotes]
                   :keys      [router]}]
  {:query         [{:router (comp/get-query MainRouter)}
                   ::app/active-remotes]
   :initial-state {:router {}}}
  (let [busy? (seq active-remotes)]
    (dom/div
      (div :.ui.top.menu
        (div :.ui.item "Demo")
        #?(:cljs
           (comp/fragment
             (ui-dropdown {:className "item" :text "Account"}
               (ui-dropdown-menu {}
                 (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this AccountList {}))} "View All")
                 (ui-dropdown-item {:onClick (fn [] (form/create! this AccountForm))} "New")))
             (ui-dropdown {:className "item" :text "Inventory"}
               (ui-dropdown-menu {}
                 (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this InventoryReport {}))} "View All")
                 (ui-dropdown-item {:onClick (fn [] (form/create! this ItemForm))} "New")))
             (ui-dropdown {:className "item" :text "Invoices"}
               (ui-dropdown-menu {}
                 (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this InvoiceList {}))} "View All")
                 (ui-dropdown-item {:onClick (fn [] (form/create! this InvoiceForm))} "New")
                 (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this AccountInvoices {:account/id (new-uuid 101)}))} "Invoices for Account 101")))
             (ui-dropdown {:className "item" :text "Reports"}
               (ui-dropdown-menu {}
                 (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this dashboard/Dashboard {}))} "Dashboard")
                 (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this sales-report/RealSalesReport {}))} "Sales Report")))))
        (div :.right.menu
          (div :.item
            (div :.ui.tiny.loader {:classes [(when busy? "active")]}))))
      (div :.ui.container.segment
        (ui-main-router router)))))

