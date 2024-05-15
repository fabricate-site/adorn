(ns site.fabricate.adorn.forms-test
  (:require [site.fabricate.adorn.forms :as forms]
            [rewrite-clj.node :as node]
            [rewrite-clj.parser :as p]
            [clojure.string :as string]
            #?(:cljs [cljs.reader :as reader])
            #?(:cljs ["fs" :as fs])
            #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t])))


(t/deftest node-data
  (t/testing "node data lifting"
    (comment
      ;; it doesn't matter whether the metadata is set via reader
      ;; dispatch or added using `with-meta` - the resulting value
      ;; is the same and it is a MetaNode
      (let [meta-vec (with-meta [:a] {:node/display-type :custom})]
        (= (forms/->node meta-vec)
           (forms/->node ^{:node/display-type :custom} [:a]))))
    (t/is (= :custom
             (:display-type (forms/node-data (with-meta
                                               (p/parse-string-all ":abc")
                                               {:node/display-type :custom
                                                :node/attr         :val})))))
    (t/is (contains? (forms/->node (with-meta (p/parse-string-all ":abc")
                                     {:node/display-type :custom
                                      :node/attr         :val}))
                     :display-type))
    (t/is (= :clj (:lang (forms/->node :abc {:lang :clj}))))
    ;; *should* these be coerced into non-metadata nodes?
    (t/is (= :val (:attr (forms/->node (node/coerce ^{:node/attr :val} [])))))
    (t/is (= :val
             (:attr (forms/->node (node/coerce
                                   (with-meta [] {:node/attr :val}))))))
    (t/is (= :val (:attr (forms/->node (node/coerce []) {:node/attr :val})))
          "node data should be set manually if present in the opts")))

(t/deftest attributes
  (t/testing "HTML attributes"
    (t/is (= {:class "language-clojure string"
              #?@(:clj [:data-java-class "java.lang.String"]
                  :cljs [:data-js-class "js/String"])}
             (forms/node-attributes (node/coerce "abc"))))
    (t/is (= {:class "language-clojure string mystr"
              #?@(:clj [:data-java-class "java.lang.String"]
                  :cljs [:data-js-class "js/String"])}
             (forms/node-attributes (node/coerce "abc") {:classes ["mystr"]}))
          "Custom HTML class names should be supported")
    (t/is (= {:class "mystr"
              #?@(:clj [:data-java-class "java.lang.String"]
                  :cljs [:data-js-class "js/String"])}
             (forms/node-attributes (node/coerce "abc") {:class-name "mystr"}))
          "Class overrides should be supported")
    (t/is (= {:class "language-clojure symbol"
              :data-clojure-symbol "my/sym"
              #?@(:clj [:data-java-class "clojure.lang.Symbol"]
                  :cljs [:data-js-class "cljs.core/Symbol"])}
             (forms/node-attributes (node/coerce 'my/sym)))
          "Data attributes for symbols should populate")
    (t/is (= {:class "language-clojure keyword"
              :data-clojure-keyword ":kw"
              #?@(:clj [:data-java-class "clojure.lang.Keyword"]
                  :cljs [:data-js-class "cljs.core/Keyword"])}
             (forms/node-attributes (node/coerce :kw)))
          "Data attributes for keywords should populate")
    (t/is (= {:class "language-clojure var"
              :data-clojure-var #?(:clj "#'clojure.core/str"
                                   :cljs "#'cljs.core/str")
              #?@(:clj [:data-java-class "clojure.lang.Var"]
                  :cljs [:data-js-class "cljs.core/Var"])}
             (forms/node-attributes (node/coerce (var str))))
          "Data attributes for vars should populate")
    (let [ws-attrs (forms/node-attributes (p/parse-string "   "))]
      (t/is (not (or (contains? ws-attrs :data-java-class)
                     (contains? ws-attrs :data-js-class)))
            "Whitespace doesn't have a class"))
    (let [num-node-attrs (forms/node-attributes
                          (assoc (node/coerce 3) :lang :clj))]
      (t/is (and (= "java.lang.Number" (:data-java-class num-node-attrs))
                 (= "language-clojure number" (:class num-node-attrs)))
            "Number attributes should work"))
    (t/is (= {:class "language-clojure string multi-line"
              :data-java-class "java.lang.String"}
             (forms/node-attributes
              (assoc (node/string-node ["a" "b"]) :lang :clj)))
          "Prefer specified node language (Clojure) to platform default")
    (t/is
     (= {:class "language-clojure string multi-line" :data-js-class "js/String"}
        (forms/node-attributes
         (assoc (node/string-node ["a" "b"]) :lang :cljs)))
     "Prefer specified node language (ClojureScript) to platform default")))

(t/deftest tokens
  (t/is (= [:span
            {#?@(:clj [:data-java-class "java.lang.String"]
                 :cljs [:data-js-class "js/String"])
             :class
             "language-clojure string"} "str"]
           (forms/token->span (node/string-node "str"))))
  (t/is (= [:span
            {#?@(:clj [:data-java-class "java.lang.String"]
                 :cljs [:data-js-class "js/String"])
             :class
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


(t/deftest forms
  (t/testing "simple forms"
    (t/is (= "language-clojure symbol"
             (get-in (forms/symbol->span (node/coerce 'ns/sym)) [1 :class])))
    (t/is (= "forms_test.cljc"
             (get-in (forms/symbol->span (node/coerce 'ns/sym)
                                         {:src/file "forms_test.cljc"})
                     [1 :src/file]))
          "Span fns should support attribute passthrough")
    (t/is (= "language-clojure keyword"
             (get-in (forms/keyword->span (node/coerce :ns/kw)) [1 :class])))
    (t/is (= "language-clojure var"
             (get-in (forms/var->span (node/coerce (var str))) [1 :class]))))
  (t/testing "composite forms:"
    (t/is (= 11 (count (forms/coll->span (node/coerce (list 1 2 3 4))))))
    (t/is (= "language-clojure list"
             (get-in (forms/coll->span (node/coerce (list 1 2 3 4)))
                     [1 :class])))
    (t/is (= "language-clojure uneval"
             (get-in (forms/uneval->span (p/parse-string "#_ :meta"))
                     [1 :class])))
    (t/is (= "language-clojure meta"
             (get-in (forms/meta->span (p/parse-string "^:meta sym"))
                     [1 :class])))
    (t/is (not (re-find
                #"unknown"
                (get-in
                 (forms/->span
                  (p/parse-string
                   "#?(:clj [:clj :vec]
                  :cljs [:cljs :vec])"))
                 [1 :class]))))
    (t/is (not (re-find
                #"unknown"
                (get-in
                 (forms/->span
                  (p/parse-string
                   "#?(:clj [:clj :vec]
                  :cljs [:cljs :vec])"))
                 [1 :class]))))
    (t/is (= "+" (peek (nth (forms/fn->span (p/parse-string "#(+ % 2)")) 4))))
    (t/is (= 5
             (count (filter #(= % :placeholder)
                            (forms/->span (node/coerce [1 2 3])
                                          {}
                                          (constantly :placeholder)))))
          "function overrides should work for child nodes")
    (t/testing "metadata"
      (let [lifted   (forms/apply-node-metadata (node/coerce ^{:k :v} []))
            lifted-2 (forms/apply-node-metadata (p/parse-string "^{:k :v} []"))]
        (t/is (= :vector (node/tag lifted))
              "Metadata application should yield child node")
        (t/is (= {:k :v} (meta lifted)) "Metadata should be applied properly")
        (t/is (contains? (meta lifted-2) :col)
              "Applied metadata should be merged")
        (t/is (= :vector
                 (node/tag (forms/apply-node-metadata (node/coerce []))))))
      ;; metadata take 2
      #_(t/is (= :custom (forms/node-form-meta)))))
  (t/testing "special forms"
    ;; not quite in the sense that Clojure uses the term
    ;; https://clojure.org/reference/special_forms
    ;; but close
    #_(t/is (= :defn
               (forms/node-type (p/parse-string "(defn ddec [x] (- x 2))")))
            "defn should be detected by node-type")
    #_(t/is (= :ns
               (forms/node-type
                (p/parse-string
                 "(ns my.custom.ns (:require '[clojure.string :as str]))"))
               "ns should be detected by node-type"))
    #_(t/is (= :let
               (forms/node-type (p/parse-string
                                 "(let [bind-sym :abc] (symbol bind-sym))")))
            "let should be detected by node-type")))

(defn parse-file
  [f]
  #?(:clj (p/parse-file-all f)
     :cljs (-> (.readFileSync fs f "utf8")
               p/parse-string-all)))

(defn check-class
  [f]
  (if (and (map? f) (contains? f :class))
    (do (t/is (not (re-find #"unknown" (:class f)))
              "all forms in source code should be parsed")
        f)
    f))

(t/deftest multiforms
  (let [forms-parsed (parse-file "src/site/fabricate/adorn/forms.cljc")
        test-parsed  (parse-file "test/site/fabricate/adorn/forms_test.cljc")
        defaults     (atom [])]
    (t/testing "src files"
      (clojure.walk/postwalk check-class (forms/->span forms-parsed)))
    (t/testing "test files"
      (clojure.walk/postwalk check-class (forms/->span test-parsed)))))
