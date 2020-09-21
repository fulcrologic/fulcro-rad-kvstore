(ns com.example.components.seed
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.type-support.decimal :as math]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write :refer [ident-of value-of]]
    [com.fulcrologic.rad.database-adapters.key-value :as key-value]
    [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
    [com.fulcrologic.rad.type-support.date-time :as dt]))

(defn new-account
  "Seed helper. Uses name as db/id (tempid)."
  [id name email password & {:as extras}]
  (let [salt #?(:clj (attr/gen-salt) :cljs 0)
        table :account/id
        value (merge
                {:account/id            id
                 :account/email         email
                 :account/name          name
                 :password/hashed-value #?(:clj (attr/encrypt password salt 100) :cljs 0)
                 :password/salt         salt
                 :password/iterations   100
                 :account/role          :account.role/user
                 :account/active?       true}
                extras)]
    [table id value]))

(defn new-address
  "Seed helper. Uses street as db/id for tempid purposes."
  [id street & {:as extras}]
  (let [table :address/id
        value (merge
                {:address/id     id
                 :address/street street
                 :address/city   "Sacramento"
                 :address/state  :address.state/CA
                 :address/zip    "99999"}
                extras)]
    [table id value]))

(defn new-category
  "Seed helper. Uses label for tempid purposes."
  [id label & {:as extras}]
  (let [table :category/id
        value (merge
                {:category/id    id
                 :category/label label}
                extras)]
    [table id value]))

(defn new-item
  "Seed helper. Uses street at db/id for tempid purposes."
  [id name price & {:as extras}]
  (let [table :item/id
        value (merge
                {:item/id    id
                 :item/name  name
                 :item/price (math/numeric price)}
                extras)]
    [table id value]))

(defn new-line-item [item quantity price & {:as extras}]
  (let [id (get extras :line-item/id (new-uuid))
        table :line-item/id
        value (merge
                {:line-item/id           id
                 :line-item/item         item
                 :line-item/quantity     quantity
                 :line-item/quoted-price (math/numeric price)
                 :line-item/subtotal     (math/* quantity price)}
                extras)]
    [table id value]))

(defn new-invoice [date customer line-items & {:as extras}]
  (let [table :invoice/id
        id (new-uuid)
        value (merge
                {:invoice/id         id
                 :invoice/customer   customer
                 :invoice/line-items line-items
                 :invoice/total      (reduce
                                       (fn [total {:line-item/keys [subtotal]}]
                                         (math/+ total subtotal))
                                       (math/zero)
                                       line-items)
                 :invoice/date       date}
                extras)]
    [table id value]))

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
        tony-address (new-address (new-uuid 1) "111 Main St.")
        tony (new-account (new-uuid 100) "Tony" "tony@example.com" "letmein"
                          :account/addresses [(ident-of (new-address (new-uuid 1) "111 Main St."))]
                          ;; Have to use value-of b/c this address doesn't exist elsewhere
                          :account/primary-address (value-of (new-address (new-uuid 300) "222 Other"))
                          :time-zone/zone-id :time-zone.zone-id/America-Los_Angeles)
        sam (new-account (new-uuid 101) "Sam" "sam@example.com" "letmein")
        sally (new-account (new-uuid 102) "Sally" "sally@example.com" "letmein")
        barb (new-account (new-uuid 103) "Barbara" "barb@example.com" "letmein")
        widget (new-item (new-uuid 200) "Widget" 33.99
                         :item/category (value-of (new-category (new-uuid 1003) "Misc")))
        screwdriver (new-item (new-uuid 201) "Screwdriver" 4.99
                              :item/category (value-of (new-category (new-uuid 1000) "Tools")))
        wrench (new-item (new-uuid 202) "Wrench" 14.99
                         :item/category (ident-of (new-category (new-uuid 1000) "Tools")))
        hammer (new-item (new-uuid 203) "Hammer" 14.99
                         :item/category (ident-of (new-category (new-uuid 1000) "Tools")))
        doll (new-item (new-uuid 204) "Doll" 4.99
                       :item/category (value-of (new-category (new-uuid 1002) "Toys")))
        robot (new-item (new-uuid 205) "Robot" 94.99
                        :item/category (ident-of (new-category (new-uuid 1002) "Toys")))
        building-blocks (new-item (new-uuid 206) "Building Blocks" 24.99
                                  :item/category (ident-of (new-category (new-uuid 1002) "Toys")))]
    [tony-address tony sam sally barb
     widget screwdriver wrench hammer doll robot building-blocks
     (new-invoice date-1 (ident-of tony)
                  [(value-of (new-line-item (ident-of doll) 1 5.0M))
                   (value-of (new-line-item (ident-of hammer) 1 14.99M))])
     (new-invoice date-2 (ident-of sally)
                  [(value-of (new-line-item (ident-of wrench) 1 12.50M))
                   (value-of (new-line-item (ident-of widget) 2 32.0M))])
     (new-invoice date-3 (ident-of sam)
                  [(value-of (new-line-item (ident-of wrench) 2 12.50M))
                   (value-of (new-line-item (ident-of hammer) 2 12.50M))])
     (new-invoice date-4 (ident-of sally)
                  [(value-of (new-line-item (ident-of robot) 6 89.99M))])
     (new-invoice date-5 (ident-of barb)
                  [(value-of (new-line-item (ident-of building-blocks) 10 20.0M))])]))

(>defn seed!
  "Get rid of all data in the database then build it again from the data structure at all-entities"
  [{::kv-key-store/keys [instance-name] :as key-store}]
  [::key-value/key-store => any?]
  (dt/set-timezone! "America/Los_Angeles")
  (println "SEEDING data (Starting fresh). For" instance-name)
  (let [tables (all-tables!)
        entities (all-entities!)]
    (kv-write/import key-store tables entities)))


