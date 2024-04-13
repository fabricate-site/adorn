(ns site.fabricate.adorn.forms-test
  (:require [site.fabricate.adorn.forms :as forms]
            [rewrite-clj.node :as node]
            [clojure.string :as string]
            #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t])))



(t/deftest attributes
  (t/testing "HTML attributes"
    (t/is (= {:class "language-clojure string"
              :data-java-class "java.lang.String"}
             (forms/node-attributes (node/coerce "abc"))))
    (t/is (= {:class "language-clojure mystr"
              :data-java-class "java.lang.String"}
             (forms/node-attributes (node/coerce "abc") {:class-name "mystr"}))
          "Custom HTML class names should be supported")
    (t/is (= {:class "language-clojure symbol"
              :data-clojure-symbol "my/sym"
              :data-java-class "clojure.lang.Symbol"}
             (forms/node-attributes (node/coerce 'my/sym)))
          "Data attributes for symbols should populate")
    (t/is (= {:class "language-clojure keyword"
              :data-clojure-keyword ":kw"
              :data-java-class "clojure.lang.Keyword"}
             (forms/node-attributes (node/coerce :kw)))
          "Data attributes for keywords should populate")
    (t/is (= {:class "language-clojure var"
              :data-clojure-var "#'clojure.core/str"
              :data-java-class "clojure.lang.Var"}
             (forms/node-attributes (node/coerce (var str))))
          "Data attributes for vars should populate")))

(t/deftest tokens
  (t/is (= [:span
            {:data-java-class "java.lang.String"
             :class "language-clojure string"} "str"]
           (forms/token->span (node/string-node "str"))))
  (t/is (= [:span
            {:data-java-class "java.lang.String"
             :class "language-clojure string multi-line"} "line1" [:br] "line2"]
           (forms/token->span (node/string-node ["line1" "line2"]))))
  (doseq [token (mapv node/coerce
                      [1 1.2
                       #?(:clj 3/5
                          :cljs 0.6) 'abc 4N 23.492982M "def" :my/kw])]
    (let [span (forms/token->span token {:class-name "example-val"})]
      (t/is (and (vector? span)
                 (re-find #"example-val" (get-in span [1 :class])))))))



(t/deftest forms)
