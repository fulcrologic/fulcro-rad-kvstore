(ns com.fulcrologic.rad.database-adapters.key-value.key-store
  "A key-store is a map that has some things in it. We spec those things, allow it to describe itself with `display`,
  and have an abstract way to create an adaptor. An adaptor is also known as a store, and is the most important thing
  in a key-store"
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]))

;;
;; Has to be not nil and can't be a channel. Wish we had a chan? function. This is the adaptor
;;
(s/def ::store some?)

(s/def ::instance-name string?)

(defn display
  "Return human readable of a key-store"
  [{::keys [instance-name] :as key-store}]
  [instance-name (->> key-store keys (mapv name))])

(defmulti make-adaptor
          "We implement a couple here, but the more heavy duty ones each have their own jar file"
          (fn [adaptor-kind options]
            adaptor-kind))

