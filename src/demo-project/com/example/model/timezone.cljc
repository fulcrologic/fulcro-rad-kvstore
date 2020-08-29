(ns com.example.model.timezone
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [taoensso.timbre :as log]
    [com.wsscode.pathom.connect :as pc]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.attributes :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.guardrails.core :refer [>defn =>]]))

(def time-zones
  "A map of time zone keys to their ZoneID string, for easy use in creating time zone enumeration in the database and
   labels in a dropdown."
  {
   :America-Chicago     "America/Chicago"
   :America-Denver      "America/Denver"
   :America-Detroit     "America/Detroit"
   :America-Los_Angeles "America/Los_Angeles"
   :America-Louisville  "America/Louisville"})

(def us-zone-names (filterv #(str/starts-with? % "US/") (vals time-zones)))

(>defn namespaced-time-zone-labels
  "Returns a time zone map with all keys prefixed properly for Datomic enumerated names. `ns` should be something like
  \"account.timezone\"."
  [ns]
  [string? => (s/map-of qualified-keyword? string?)]
  (into {}
    (map (fn [[k v]] [(keyword ns (name k)) (str/replace v "_" " ")]))
    time-zones))

(>defn namespaced-time-zone-ids
  "Returns a time zone map with all keys prefixed properly for Datomic enumerated names. `ns` should be something like
  \"account.timezone\"."
  [ns]
  [string? => (s/map-of qualified-keyword? string?)]
  (into {}
    (map (fn [[k v]] [(keyword ns (name k)) v]))
    time-zones))

(def datomic-time-zones (namespaced-time-zone-ids "time-zone.zone-id"))

(defattr zone-id :time-zone/zone-id :enum
  {ao/required?         true
   ao/identities        #{:account/id}
   ao/schema            :production
   ao/enumerated-values (set (keys datomic-time-zones))
   ao/enumerated-labels datomic-time-zones
   fo/field-label       "Time Zone"
   ;; Enumerations with lots of values should use autocomplete instead of pushing all possible values to UI
   fo/field-style       :autocomplete
   fo/field-options     {:autocomplete/search-key    :autocomplete/time-zone-options
                         :autocomplete/debounce-ms   100
                         :autocomplete/minimum-input 1}})

#?(:clj
   (pc/defresolver all-time-zones [{:keys [query-params]} _]
     {::pc/output [{:autocomplete/time-zone-options [:text :value]}]}
     (let [{:keys [only search-string]} query-params]
       {:autocomplete/time-zone-options
        (cond
          (keyword? only)
          [{:text (str/replace (name only) "_" " ") :value only}]

          (seq search-string)
          (let [search-string (str/lower-case search-string)]
            (into []
              (comp
                (map (fn [[k v]] (array-map :text (str/replace v "_" " ") :value k)))
                (filter (fn [{:keys [text]}] (str/includes? (str/lower-case text) search-string)))
                (take 10))
              datomic-time-zones))

          :else
          us-zone-names)})))

(def attributes [zone-id])
#?(:clj
   (def resolvers [all-time-zones]))
