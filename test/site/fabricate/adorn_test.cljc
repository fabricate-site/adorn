(ns site.fabricate.adorn-test
  (:require [site.fabricate.adorn.forms :as forms]
            [site.fabricate.adorn :as adorn]
            [rewrite-clj.node :as node]
            [rewrite-clj.parser :as p]
            [clojure.string :as string]
            #?(:cljs [cljs.reader :as reader])
            #?(:cljs ["fs" :as fs])
            #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t])))


(defn custom-dispatch
  [node]
  (let [form-meta (forms/node-form-meta node)]
    ;; example elision of adorn-specific metadata
    (if (= #{:display/type} (set (keys form-meta)))
      (let [child-node  (peek (node/children node))
            node-tag    (node/tag child-node)
            tag-method  (get-method adorn/node->hiccup node-tag)
            node-hiccup (tag-method node)]
        (update-in node-hiccup [1 :class] str " custom-type"))
      (let [node-tag    (node/tag node)
            tag-method  (get-method adorn/node->hiccup node-tag)
            node-hiccup (tag-method node)]
        (update-in node-hiccup [1 :class] str " custom-type")))))


(defmethod adorn/node->hiccup :custom [node _opts] (custom-dispatch node))


(t/deftest api
  (t/testing "node info + metadata"
    (t/is (= :custom
             (adorn/form-type (forms/->node
                               "^{:display/type :custom} {:a 2}"))))
    (t/is (= :custom
             (adorn/form-type (forms/->node ^{:display/type :custom} {:a 2})))))
  (t/testing "dispatch"
    (t/is (some? (adorn/clj->hiccup :abc {})))
    (t/is (some? (adorn/clj->hiccup [:abc {:a 3} (fn [i] 3)] {})))
    #_(let [str-hiccup  (adorn/clj->hiccup "^{:display/type :custom} {:a 2}")
            expr-hiccup (let [m ^{:display/type :custom} {:a 2}]
                          (adorn/clj->hiccup m))]
        (t/is (re-find #"custom" (get-in str-hiccup [1 :class]))
              "Dispatch based on :type metadata should work")
        (t/is (re-find #"custom" (get-in expr-hiccup [1 :class]))
              "Dispatch based on :type metadata should work"))))
