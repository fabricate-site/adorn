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
  ([node {:keys [display-type] :as opts}]
   (if (= :forms (node/tag node))
     (into [:span {:class "language-clojure custom-type"}]
           (map #(custom-dispatch (forms/->node %)) (node/children node)))
     ;; example elision of adorn-specific metadata
     (if (or (contains? node :display-type)
             (contains? (meta node) :display-type)
             display-type)
       ;; TODO: investigate first/peek further
       (let [node-tag    (node/tag node)
             tag-method  (get-method adorn/form->hiccup node-tag)
             node-hiccup (tag-method node)]
         (update-in node-hiccup [1 :class] str " custom-type"))
       node))))

;; ultimately this likely means providing a convenience higher-order
;; function to make it easier for users to dispatch "the right way"
;; on metadata set in source code - the above example amply demonstrates that
;; it's difficult even for me, the author of the library, to do it correctly




(defmethod adorn/form->hiccup :custom
  ([node opts] (custom-dispatch node opts))
  ([node] (custom-dispatch node {})))

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

(t/deftest api
  (t/testing ":lang option"
    (t/is (= "cljs.core/Keyword"
             (get-in (adorn/clj->hiccup (forms/->node :a {:lang :cljs}))
                     [1 :data-js-class]))
          ":lang option should carry through to results")
    (t/is (= "cljs.core/Keyword"
             (get-in (adorn/clj->hiccup :a {:lang :cljs}) [1 :data-js-class]))
          ":lang option should carry through to results")
    (t/is (= "clojure.lang.Keyword"
             (get-in (adorn/clj->hiccup :a {:lang :clj}) [1 :data-java-class]))
          ":lang option should carry through to results")
    (t/is (= "clojure.lang.Keyword"
             (get-in (adorn/clj->hiccup (forms/->node :a {:lang :clj}))
                     [1 :data-java-class]))
          ":lang option should carry through to results"))
  (t/testing "node info + metadata"
    (t/is (= :custom
             (adorn/form-type (forms/->node "{:a 2}" {:display-type :custom}))))
    (t/is (= :custom
             (adorn/form-type (forms/->node ^{:node/display-type :custom}
                                            {:a 2})))))
  (t/testing "dispatch"
    (t/is (some? (adorn/clj->hiccup :abc {})))
    (t/is (some? (adorn/clj->hiccup [:abc {:a 3} (fn [i] 3)] {})))
    (t/is (= :custom
             (adorn/form-type (forms/->node
                               (p/parse-string
                                "^{:node/display-type :custom} {:a 2}")))))
    (t/is (= :custom
             (adorn/form-type (forms/->node ^{:node/display-type :custom}
                                            {:a 2}))))
    (let [str-hiccup    (adorn/clj->hiccup
                         (p/parse-string
                          "^{:node/display-type :custom} {:a 2}"))
          str-hiccup-2  (adorn/clj->hiccup
                         "^{:node/display-type :custom} {:a 2}")
          expr-hiccup   (let [m ^{:node/display-type :custom} {:a 2}]
                          (adorn/clj->hiccup m))
          expr-hiccup-2 (adorn/clj->hiccup :kw {:display-type :custom})]
      (t/is (re-find #"custom" (get-in str-hiccup [1 :class]))
            "Dispatch based on :node/display-type metadata should work")
      (t/is (re-find #"custom" (get-in (first str-hiccup-2) [1 :class] ""))
            "Dispatch based on :node/display-type metadata should work")
      (t/is (re-find #"custom" (get-in expr-hiccup [1 :class]))
            "Dispatch based on :type metadata should work")
      (t/is (re-find #"custom" (get-in expr-hiccup-2 [1 :class]))
            "Dispatch based on option passed in should work"))
    #_(t/testing "with map"
        (let [map->dl     (fn map->dl [m-node _opts]
                            (into [:dl]
                                  (mapcat (fn [[k v]] [[:dt k] [:dd v]])
                                          (node/sexpr m-node))))
              test-map    {:term "definition" :term2 "definition"}
              expected-dl [:dl [:dt :term] [:dd "definition"] [:dt :term2]
                           [:dd "definition"]]]
          (t/is (= expected-dl
                   (adorn/clj->hiccup test-map {:display-type map->dl}))
                "passing a function should change the display type")
          (t/is (= expected-dl
                   (adorn/clj->hiccup test-map {:display-type {:map map->dl}}))
                "passing a map should change the display type")
          (t/is (= expected-dl
                   (adorn/clj->hiccup test-map
                                      {:display-type {:self* map->dl}}))
                "passing a map should change the display type")))))

(comment
  (adorn/clj->hiccup {:a 1 :b 2}
                     {:display-type (fn ident [i & _args] (node/sexpr i))})
  (adorn/form-type (forms/->node {:a 1 :b 2})
                   {:display-type {:map (fn ident [i & _args] i)}}))

(t/deftest src-files
  (let [forms-parsed (parse-file "src/site/fabricate/adorn/forms.cljc")
        test-parsed  (parse-file "test/site/fabricate/adorn/forms_test.cljc")
        defaults     (atom [])]
    (t/testing "src files"
      (clojure.walk/postwalk check-class (forms/->span forms-parsed)))
    (t/testing "test files"
      (clojure.walk/postwalk check-class (forms/->span test-parsed)))))

(comment
  (adorn/clj->hiccup (forms/->node (p/parse-string
                                    "^{:node/display-type :custom} {:a 2}")))
  (adorn/form-type (forms/->node (p/parse-string
                                  "^{:node/display-type :custom} {:a 2}"))))
