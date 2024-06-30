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
    (let [node-1       (node/coerce
                        ^{:type :something :node/type :something-else} {:a 1})
          node-2       (node/coerce ^{:node/type :something-else} {:a 1})
          str-vec-node (p/parse-string "^{:node/display-type :custom} [:abc]")
          kw-only      (node/coerce ^:kw [1 2 3])
          kw-only-str  (p/parse-string "^:kw [1 2 3]")]
      (t/is
       (= :vector (:tag (forms/apply-node-metadata str-vec-node)))
       "forms with node-only metadata should be converted to the correct type after splitting")
      (t/is
       (= :map (:tag (forms/apply-node-metadata node-2)))
       "Node metadata splitting should yield non-metadata nodes for node-only keywords")
      (let [split-1 (forms/apply-node-metadata node-1)]
        (t/is
         (= :meta (:tag split-1))
         "Node metadata splitting should yield metadata nodes for non-node keywords")
        (t/is
         (= [:something :something-else]
            [(get-in split-1 [:children 0 :children 2 :k]) (:type split-1)])
         "node metadata should be split appropriately and directly accessible on the resulting node"))
      (let [kw-split (forms/apply-node-metadata kw-only-str)]
        (t/is (= :meta (:tag kw-split))
              "metadata splitting should work for keyword-only nodes")
        (t/is (= [:kw [1 2 3]] (node/sexprs (:children kw-split)))))
      (t/testing "expected data model"
        (let [parsed-node
              (-> "^{:node/type :example :node/display-type :example} [1]"
                  (p/parse-string)
                  (forms/apply-node-metadata))
              conflict-node (forms/apply-node-metadata
                             (node/coerce ^{:node/tag :custom} [1]))]
          (t/is (and (= {:type         :example
                         :display-type :example
                         :src-info     {:row 1 :col 1 :end-row 1 :end-col 55}}
                        (select-keys parsed-node
                                     [:type :display-type :src-info])))
                ":node-prefixed keywords should be merged")
          (t/is (not (= :custom (:tag conflict-node)))
                "reserved rewrite-clj tags should be ignored"))))
    (t/is (= :custom
             (:display-type (forms/->node (with-meta (p/parse-string-all ":abc")
                                                     {:node/display-type :custom
                                                      :node/attr :val})))))
    (t/is (contains? (forms/->node (with-meta (p/parse-string-all ":abc")
                                              {:node/display-type :custom
                                               :node/attr         :val}))
                     :display-type))
    (t/is (= :clj (:lang (forms/->node :abc {:lang :clj}))))
    ;; *should* these be coerced into non-metadata nodes?
    #_(t/is (= :val (:attr (forms/->node (node/coerce ^{:node/attr :val} [])))))
    #_(t/is (= :val
               (:attr (forms/->node (node/coerce
                                     (with-meta [] {:node/attr :val}))))))
    (t/is (= :val (:attr (forms/->node (node/coerce []) {:node/attr :val})))
          "node data should be set manually if present in the opts"))
  (t/testing "node normalization"
    (t/is (= :clj (:lang (forms/->node (node/coerce "1") {:lang :clj}))))
    (t/is (= :clj
             (:lang (forms/->node (forms/->node (node/coerce "1")
                                                {:lang :clj}))))
          "normalization should be idempotent")
    (t/is (= #?(:clj :clj
                :cljs :cljs)
             (get-in (forms/->node (node/coerce [1 {:a :b}])
                                   {:update-subnodes? true})
                     [:children 1 :lang]))
          "node :lang should be set recursively for all child nodes")
    (t/is (= :map
             (-> (node/coerce [1 {:a :b}])
                 (forms/->node {:lang :cljs :update-subnodes? true})
                 :children
                 last
                 :tag))
          "subnodes should have correct types after updating")
    (t/is (= :custom
             (-> (node/coerce [1 ^{:node/display-type :custom} {:a :b}])
                 (forms/->node {:update-subnodes? true})
                 :children
                 last
                 meta
                 (get :display-type)))
          "node metadata should be split recursively for all child nodes")
    (t/is (= "cljs.core/Keyword"
             (get-in (forms/->span (forms/->node :a {:lang :cljs}))
                     [1 :data-js-class]))
          ":lang option should carry through to results")
    (t/is (= "cljs.core/Keyword"
             (get-in (forms/->span :a {:lang :cljs}) [1 :data-js-class]))
          ":lang option should carry through to results")
    (t/is (= "clojure.lang.Keyword"
             (get-in (forms/->span :a {:lang :clj}) [1 :data-java-class]))
          ":lang option should carry through to results")
    (t/is (= "clojure.lang.Keyword"
             (get-in (forms/->span (forms/->node :a {:lang :clj}))
                     [1 :data-java-class]))
          ":lang option should carry through to results")))

(comment
  (forms/->node (node/coerce [1 ^{:node/display-type :custom} {:a :b}]))
  (meta (forms/apply-node-metadata (node/coerce ^{:node/display-type :custom}
                                                {:a :b}))))

(t/deftest attributes
  (t/testing "HTML attributes"
    (t/is (= {:class "language-clojure string"
              #?@(:clj [:data-java-class "java.lang.String"]
                  :cljs [:data-js-class "js/String"])}
             (forms/node-attributes (node/coerce "abc"))))
    (t/is (= {:class "language-clojure string"
              :data-java-class "java.lang.String"}
             (forms/node-attributes (merge (node/coerce "abc") {:lang :clj})))
          "designated language should be supported")
    (t/is (= {:class "language-clojure string" :data-js-class "js/String"}
             (forms/node-attributes (forms/->node (node/coerce "abc")
                                                  {:lang :cljs})))
          "designated language should be supported")
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
    (t/is (= "js/String"
             (get-in (forms/->span (node/coerce "b") {:lang :cljs})
                     [1 :data-js-class]))
          "Language overrides should be supported")
    (t/is (= "js/String"
             (get-in (forms/->span [1 2 3 ["b"]] {:lang :cljs})
                     [9 3 1 :data-js-class]))
          "Language overrides should be supported")
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
             :class "language-clojure string"} "\"" "str" "\""]
           (forms/token->span (node/string-node "str"))))
  (t/is (= [:span
            {#?@(:clj [:data-java-class "java.lang.String"]
                 :cljs [:data-js-class "js/String"])
             :class "language-clojure string multi-line"} "\"" "line1" [:br]
            "line2" "\""]
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
          "function overrides should work for child nodes"))
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
      (clojure.walk/postwalk check-class (forms/->span test-parsed)))
    #_(doseq [form (:children forms-parsed)]
        (try (forms/->span form)
             (catch Exception e #_(tap> form) (println form))))))
