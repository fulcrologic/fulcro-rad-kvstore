(ns com.example.client
  (:require
    [com.example.ui :as ui :refer [Root]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.rad.application :as rad-app]
    [com.fulcrologic.rad.report :as report]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.rendering.semantic-ui.semantic-ui-controls :as sui]
    [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]
    [taoensso.timbre :as log]
    [taoensso.tufte :as tufte :refer [profile]]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [com.fulcrologic.rad.routing.html5-history :as hist5 :refer [html5-history]]
    [com.fulcrologic.rad.routing.history :as history]
    [com.fulcrologic.fulcro.algorithms.tx-processing.synchronous-tx-processing :as stx]
    [com.fulcrologic.rad.routing :as routing]
    [com.fulcrologic.fulcro.components :as comp]))

(defonce stats-accumulator
  (tufte/add-accumulating-handler! {:ns-pattern "*"}))

(defonce app (rad-app/fulcro-rad-app
               {:client-did-mount (fn [app]
                                    (hist5/restore-route! app ui/LandingPage {}))
                ;; Loading indicator in top RH corner going forever
                ;:submit-transaction! stx/sync-tx!
                }))

(defn refresh []
  ;; hot code reload of installed controls
  (log/info "Reinstalling controls")
  (rad-app/install-ui-controls! app sui/all-controls)
  (report/install-formatter! app :boolean :affirmation (fn [_ value] (if value "yes" "no")))
  (app/mount! app Root "app"))

(defn init []
  (log/info "Starting App")
  (log/merge-config! {:output-fn prefix-output-fn
                      :appenders {:console (console-appender)}})
  ;; a default tz until they log in
  (datetime/set-timezone! "America/Los_Angeles")
  (history/install-route-history! app (html5-history))
  (rad-app/install-ui-controls! app sui/all-controls)
  (report/install-formatter! app :boolean :affirmation (fn [_ value] (if value "yes" "no")))
  (app/mount! app Root "app"))

(defonce performance-stats (tufte/add-accumulating-handler! {}))

(defn pperf
  "Dump the currently-collected performance stats"
  []
  (let [stats (not-empty @performance-stats)]
    (println (tufte/format-grouped-pstats stats))))
