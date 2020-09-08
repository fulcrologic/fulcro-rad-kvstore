(ns com.example.components.seeded-connection
  (:require
    [mount.core :refer [defstate]]
    [com.example.model :refer [all-attributes]]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.example.components.config :as config]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write :refer [ident-of value-of]]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.example.model.seed :as seed]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.type-support.date-time :as dt]
    [com.fulcrologic.rad.database-adapters.key-value.database :as kv-database]))

(defn all-tables!
  "All the tables that we are going to have entities of. This information is in the RAD registry, we just haven't gone
  that far yet"
  []
  [:account/id :item/id :invoice/id :line-item/id :category/id :address/id])

(defn all-entities!
  "There is no check done on the integrity of this data. But when putting it together the functions ident-of and value-of are
  supposed to help. Just make sure that for every ident-of there is at least one value-of of the same entity"
  []
  (let [date-1 (dt/html-datetime-string->inst "2020-01-01T12:00")
        date-2 (dt/html-datetime-string->inst "2020-01-05T12:00")
        date-3 (dt/html-datetime-string->inst "2020-02-01T12:00")
        date-4 (dt/html-datetime-string->inst "2020-03-10T12:00")
        date-5 (dt/html-datetime-string->inst "2020-03-21T12:00")
        tony-address (seed/new-address (new-uuid 1) "111 Main St.")
        tony (seed/new-account (new-uuid 100) "Tony" "tony@example.com" "letmein"
                               :account/addresses [(ident-of (seed/new-address (new-uuid 1) "111 Main St."))]
                               ;; Have to use value-of b/c this address doesn't exist elsewhere
                               :account/primary-address (value-of (seed/new-address (new-uuid 300) "222 Other"))
                               :time-zone/zone-id :time-zone.zone-id/America-Los_Angeles)
        sam (seed/new-account (new-uuid 101) "Sam" "sam@example.com" "letmein")
        sally (seed/new-account (new-uuid 102) "Sally" "sally@example.com" "letmein")
        barb (seed/new-account (new-uuid 103) "Barbara" "barb@example.com" "letmein")
        widget (seed/new-item (new-uuid 200) "Widget" 33.99
                              :item/category (value-of (seed/new-category (new-uuid 1003) "Misc")))
        screwdriver (seed/new-item (new-uuid 201) "Screwdriver" 4.99
                                   :item/category (value-of (seed/new-category (new-uuid 1000) "Tools")))
        wrench (seed/new-item (new-uuid 202) "Wrench" 14.99
                              :item/category (ident-of (seed/new-category (new-uuid 1000) "Tools")))
        hammer (seed/new-item (new-uuid 203) "Hammer" 14.99
                              :item/category (ident-of (seed/new-category (new-uuid 1000) "Tools")))
        doll (seed/new-item (new-uuid 204) "Doll" 4.99
                            :item/category (value-of (seed/new-category (new-uuid 1002) "Toys")))
        robot (seed/new-item (new-uuid 205) "Robot" 94.99
                             :item/category (ident-of (seed/new-category (new-uuid 1002) "Toys")))
        building-blocks (seed/new-item (new-uuid 206) "Building Blocks" 24.99
                                       :item/category (ident-of (seed/new-category (new-uuid 1002) "Toys")))]
    [tony-address tony sam sally barb
     widget screwdriver wrench hammer doll robot building-blocks
     (seed/new-invoice date-1 (ident-of tony)
                       [(value-of (seed/new-line-item (ident-of doll) 1 5.0M))
                        (value-of (seed/new-line-item (ident-of hammer) 1 14.99M))])
     (seed/new-invoice date-2 (ident-of sally)
                       [(value-of (seed/new-line-item (ident-of wrench) 1 12.50M))
                        (value-of (seed/new-line-item (ident-of widget) 2 32.0M))])
     (seed/new-invoice date-3 (ident-of sam)
                       [(value-of (seed/new-line-item (ident-of wrench) 2 12.50M))
                        (value-of (seed/new-line-item (ident-of hammer) 2 12.50M))])
     (seed/new-invoice date-4 (ident-of sally)
                       [(value-of (seed/new-line-item (ident-of robot) 6 89.99M))])
     (seed/new-invoice date-5 (ident-of barb)
                       [(value-of (seed/new-line-item (ident-of building-blocks) 10 20.0M))])]))

(>defn seed!
  "Get rid of all data in the database then build it again from the data structure at all-entities"
  [{:keys [instance-name] :as connection}]
  [::key-value/key-store => any?]
  (dt/set-timezone! "America/Los_Angeles")
  (println "SEEDING data (Starting fresh). For" instance-name)
  (kv-database/destructive-reset connection (all-tables!) (all-entities!)))

;;
;; We've got a tiny database so let's seed it every time we refresh
;; Far less confusing not to have this :on-reload thing - change the seed function and it will be run!
;; ^{:on-reload :noop}
;;
(defstate kv-connections
  "The connection to the database that has just been freshly populated"
  :start (let [{:keys [main] :as databases} (kv-database/start config/config)]
           (seed! main)
           databases))
