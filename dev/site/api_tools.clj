(ns site.api-tools
  (:require [site.fabricate.adorn :as adorn]
            [site.fabricate.adorn.forms :as forms]
            [cybermonday.core :as md]
            [site.functions :as functions]))

(defn var-type
  [v]
  (let [vv (var-get v)]
    (cond (and (fn? vv)) "function"
          :default       (.getName (type vv)))))


(defn document-ns-var
  [[_sym v]]
  (let [v-meta (meta v)]
    [:div {:class "var-doc"} [:h2 {:class "var-name"} [:code _sym]]
     [:code {:class "var-type"} (var-type v)]
     (when (:doc v-meta)
       (let [[_t _attrs & contents] (md/parse-body (:doc v-meta))]
         (apply list contents)))
     (when (= clojure.lang.MultiFn (var-type v))
       [:div {:class "method-list"} [:h4 "Implemented methods:"]
        (into [:p]
              (->> (.getMethodTable (var-get v))
                   (map (fn [[method-name method]] [:code
                                                    {:class "clojure-method"}
                                                    (adorn/clj->hiccup
                                                     method-name)]))
                   (interpose ", ")))])]))

(comment
  (var-type #'site.fabricate.adorn/form->hiccup)
  (mapv #(meta (peek %)) (ns-publics 'site.fabricate.adorn))
  (.getMethodTable site.fabricate.adorn/form->hiccup)
  (md/parse-body
   "  Extensible display of arbitrary Clojure values and rewrite-clj nodes as Hiccup elements.

Falls back to defaults defined in `site.fabricate.adorn.forms` namespace for unimplemented values."))
