(ns com.example.ui.landing-page
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom :refer [div label input]]
       :cljs [com.fulcrologic.fulcro.dom :as dom :refer [div label input]])))

(defsc LandingPage [this props]
  {:query         ['*]
   :ident         (fn [] [:component/id ::LandingPage])
   :initial-state {}
   :route-segment ["landing-page"]}
  (dom/div "Welcome to the Demo. Please log in."))

