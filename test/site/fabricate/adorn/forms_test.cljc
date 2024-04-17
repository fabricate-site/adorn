(ns site.fabricate.adorn.forms-test
  (:require [site.fabricate.adorn.forms :as forms]
            [rewrite-clj.node :as node]
            [rewrite-clj.parser :as p]
            [clojure.string :as string]
            #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t])))



(t/deftest attributes
  (t/testing "HTML attributes"
    (t/is (= {:class "language-clojure string"
              #?@(:clj [:data-java-class "java.lang.String"])}
             (forms/node-attributes (node/coerce "abc"))))
    (t/is (= {:class "language-clojure mystr"
              #?@(:clj [:data-java-class "java.lang.String"])}
             (forms/node-attributes (node/coerce "abc") {:class-name "mystr"}))
          "Custom HTML class names should be supported")
    (t/is (= {:class "language-clojure symbol"
              :data-clojure-symbol "my/sym"
              #?@(:clj [:data-java-class "clojure.lang.Symbol"])}
             (forms/node-attributes (node/coerce 'my/sym)))
          "Data attributes for symbols should populate")
    (t/is (= {:class "language-clojure keyword"
              :data-clojure-keyword ":kw"
              #?@(:clj [:data-java-class "clojure.lang.Keyword"])}
             (forms/node-attributes (node/coerce :kw)))
          "Data attributes for keywords should populate")
    ;; TODO: cljs handles vars differently so this needs to be rethought
    (t/is (= {:class "language-clojure var"
              :data-clojure-var #?(:clj "#'clojure.core/str"
                                   :cljs "#'cljs.core/str")
              #?@(:clj [:data-java-class "clojure.lang.Var"])}
             (forms/node-attributes (node/coerce (var str))))
          "Data attributes for vars should populate")))

(t/deftest tokens
  (t/is (= [:span
            {#?@(:clj [:data-java-class "java.lang.String"]) :class
             "language-clojure string"} "str"]
           (forms/token->span (node/string-node "str"))))
  (t/is (= [:span
            {#?@(:clj [:data-java-class "java.lang.String"]) :class
             "language-clojure string multi-line"} "line1" [:br] "line2"]
           (forms/token->span (node/string-node ["line1" "line2"]))))
  (doseq [token     (mapv node/coerce
                          [1 1.2
                           #?(:clj 3/5
                              :cljs 0.6) 'abc 4N 23.492982M "def" :my/kw])
          str-token (mapv p/parse-string ["3/6"])]
    (let [span (forms/token->span token {:class-name "example-val"})]
      (t/is (and (vector? span)
                 (re-find #"example-val" (get-in span [1 :class])))))))



(t/deftest forms)
