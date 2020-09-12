(ns com.fulcrologic.rad.database-adapters.key-value.key-store
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]))

;;
;; TBH this way, not having fully qualified names, was at least as good!
;; OTOH I suppose this kv-key-store could be used as an env of sorts, where other keys are put in
;; and we don't want clashes.
;(def key-store? (every-pred map? :store :instance-name))
;;
;; Konserve doesn't have spec AFAIK. We could do more than nothing however...
;;
(s/def ::store any?)

(s/def ::instance-name string?)

