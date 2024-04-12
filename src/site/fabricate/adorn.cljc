(ns site.fabricate.adorn
  "Single-function API for adorn."
  (:require #_[site.fabricate.adorn.forms :as forms] ; refactor target TBD
            [site.fabricate.adorn.parse :as parse]
            [rewrite-clj.node :as node]))

;; TBD on whether other options should be passed through

(defn clj->hiccup
  ([src {display-type :display/type
         :or {display-type (:display/type (meta src))}
         :as opts}]
   (cond (node/node? src) (parse/node->hiccup src)
         (string? src) (parse/str->hiccup src) ;; need to pass display type here
         :default ;; assume rest is expr
         (parse/expr->hiccup src)))
  ([src] (clj->hiccup src {})))
