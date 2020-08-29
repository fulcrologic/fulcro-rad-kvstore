(ns com.example.model.seed
  (:require
    [com.fulcrologic.rad.type-support.decimal :as math]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.attributes :as attr]))

(defn new-account
  "Seed helper. Uses name as db/id (tempid)."
  [id name email password & {:as extras}]
  (let [salt (attr/gen-salt)
        table :account/id
        value (merge
                {:account/id            id
                 :account/email         email
                 :account/name          name
                 :password/hashed-value (attr/encrypt password salt 100)
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

