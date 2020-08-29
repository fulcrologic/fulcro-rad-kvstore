(ns com.example.components.database-queries
  (:require
    [taoensso.timbre :as log]))

(defn get-all-accounts
  [env query-params]
  (if-let [db nil]
    (let [ids (if (:show-inactive? query-params)
                []
                [])]
      (mapv (fn [id] {:account/id id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-all-items
  [env {:category/keys [id]}]
  (if-let [db nil]
    (let [ids (if id
                []
                [])]
      (mapv (fn [id] {:item/id id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-customer-invoices [env {:account/keys [id]}]
  (if-let [db nil]
    (let [ids []]
      (mapv (fn [id] {:invoice/id id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-all-invoices
  [env query-params]
  (if-let [db nil]
    (let [ids []]
      (mapv (fn [id] {:invoice/id id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-invoice-customer-id
  [env invoice-id]
  (if-let [db nil]
    nil
    (log/error "No database atom for production schema!")))

(defn get-all-categories
  [env query-params]
  (if-let [db nil]
    (let [ids []]
      (mapv (fn [id] {:category/id id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-line-item-category [env line-item-id]
  (if-let [db nil]
    (let [id []]
      id)
    (log/error "No database atom for production schema!")))
