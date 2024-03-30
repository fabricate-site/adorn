(ns site.fabricate.adorn.parse-test
  (:require
   #?(:clj [clojure.test :as t]
      :cljs [cljs.test :as t])
   #?(:clj [clojure.spec.alpha :as spec])
   #?(:clj [clojure.core.specs.alpha :as specs]
      :cljs [cljs.core.specs.alpha :as specs])
   [clojure.test.check :as check]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [clojure.test.check.clojure-test :refer [defspec]]
   [site.fabricate.adorn.parse :as parse]
   [rewrite-clj.node :as node]))

(defn custom-dispatch
  [node]
  (let [form-meta (parse/node-form-meta node)]
    ;; example elision of adorn-specific metadata
    (if (= #{:display/type} (set (keys form-meta)))
      (let [child-node  (peek (node/children node))
            node-tag    (node/tag child-node)
            tag-method  (get-method parse/node->hiccup node-tag)
            node-hiccup (tag-method node)]
        (update-in node-hiccup [1 :class] str " custom-type"))
      (let [node-tag    (node/tag node)
            tag-method  (get-method parse/node->hiccup node-tag)
            node-hiccup (tag-method node)]
        (update-in node-hiccup [1 :class] str " custom-type")))))

(defmethod parse/node->hiccup :custom [node] (custom-dispatch node))

(t/deftest exprs
  (t/testing "source code display"
    (t/is (= [:span {:class "language-clojure list"}
              [:span {:class "language-clojure dispatch"} "#"
               [:span {:class "language-clojure open-paren"} "("]]
              [:span {:class "language-clojure symbol"} "+"]
              [:span {:class "language-clojure whitespace"} " "]
              [:span {:class "language-clojure number"} "3"]
              [:span {:class "language-clojure whitespace"} " "]
              [:span {:class "language-clojure symbol"} "%"]
              [:span {:class "language-clojure close-paren"} ")"]]
             (#'parse/fn-node->hiccup (node/coerce '#(+ 3 %)))
             (#'parse/expr->hiccup '#(+ 3 %))))
    (t/is (= [:span {:class "language-clojure list"}
              [:span {:class "language-clojure dispatch"} "#"
               [:span {:class "language-clojure open-paren"} "("]]
              [:span {:class "language-clojure symbol"} "+"]
              [:span {:class "language-clojure whitespace"} " "]
              [:span {:class "language-clojure number"} "3"]
              [:span {:class "language-clojure whitespace"} " "]
              [:span {:class "language-clojure symbol"} "%1"]
              [:span {:class "language-clojure whitespace"} " "]
              [:span {:class "language-clojure symbol"} "%2"]
              [:span {:class "language-clojure close-paren"} ")"]]
             (#'parse/fn-node->hiccup (node/coerce '#(+ 3 %1 %2)))
             (#'parse/expr->hiccup '#(+ 3 %1 %2))))
    (t/is (= [:span {:class "language-clojure list"}
              [:span {:class "language-clojure dispatch"} "#"
               [:span {:class "language-clojure open-paren"} "("]]
              [:span {:class "language-clojure symbol"} "apply"]
              [:span {:class "language-clojure whitespace"} " "]
              [:span {:class "language-clojure symbol"} "+"]
              [:span {:class "language-clojure whitespace"} " "]
              [:span {:class "language-clojure number"} "3"]
              [:span {:class "language-clojure whitespace"} " "]
              [:span {:class "language-clojure symbol"} "%&amp;"]
              [:span {:class "language-clojure close-paren"} ")"]]
             (#'parse/fn-node->hiccup (node/coerce '#(apply + 3 %&)))))
    (t/is (= [:span {:class "language-clojure comment"} ";" " a comment" [:br]]
             (parse/str->hiccup "; a comment\n(+ 3 4)")))
    (t/is (some? (parse/str->hiccup "(defn myfunc [a] \n; commentary\n (inc a))")))
    (t/is (= [:span {:class "language-clojure keyword"} ":"
              [:span {:class "language-clojure keyword-ns"} "ns"] "/"
              [:span {:class "language-clojure keyword-name"} "kw"]]
             (parse/expr->hiccup :ns/kw)))
    (t/is (some? (re-find #"quote"
                          (get-in (parse/str->hiccup "'(+ something something)")
                                  [1 :class])))
          "Quoted expressions should be correctly identified"))
  (let [str-hiccup  (parse/str->hiccup "^{:display/type :custom} {:a 2}")
        expr-hiccup (let [m ^{:display/type :custom} {:a 2}] (parse/expr->hiccup m))]
    (t/is (re-find #"custom" (get-in str-hiccup [1 :class]))
          "Dispatch based on :type metadata should work")
    (t/is (re-find #"custom" (get-in expr-hiccup [1 :class]))
          "Dispatch based on :type metadata should work")))

(def convertible?
  (prop/for-all
   [form (gen/one-of
          [gen/any
           #_(spec/gen 'clojure.core/fn) ;; doesn't work yet
           #_(spec/gen 'clojure.core/defn) ;; doesn't work yet
           ])]
   (vector? (parse/expr->hiccup form))))

(defspec any-val 2500 convertible?)
