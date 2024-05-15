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
  (if (= :forms (node/tag node))
    ;; this is fine, just pass in the options
    (apply list (map custom-dispatch (node/children node)))
    ;; example elision of adorn-specific metadata
    (if (contains? node :display-type)
      ;; TODO: investigate first/peek further
      (let [child-node  (last (node/children node))
            node-tag    (node/tag child-node)
            tag-method  (get-method adorn/node->hiccup node-tag)
            node-hiccup (tag-method node)]
        (update-in node-hiccup [1 :class] str " custom-type"))
      (let [node-tag    (node/tag node)
            tag-method  (get-method adorn/node->hiccup node-tag)
            node-hiccup (tag-method node)]
        (update-in node-hiccup [1 :class] str " custom-type")))))

;; ultimately this likely means providing a convenience higher-order
;; function to make it easier for users to dispatch "the right way"
;; on metadata set in source code - the above example amply demonstrates that
;; it's difficult even for me, the author of the library, to do it correctly




(defmethod adorn/node->hiccup :custom
  ([node _opts] (custom-dispatch node))
  ([node] (custom-dispatch node)))

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
  (t/testing "node info + metadata"
    (t/is (= :custom
             (adorn/form-type (forms/->node "{:a 2}" {:display-type :custom}))))
    (t/is (= :custom
             (adorn/form-type (forms/->node ^{:node/display-type :custom}
                                            {:a 2})))))
  (t/testing "dispatch"
    (t/is (some? (adorn/clj->hiccup :abc {})))
    (t/is (some? (adorn/clj->hiccup [:abc {:a 3} (fn [i] 3)] {})))
    ;; TODO: why do these have a missing caret?
    (let [str-hiccup    (adorn/clj->hiccup (p/parse-string
                                            "^{:display-type :custom} {:a 2}"))
          str-hiccup-2  (adorn/clj->hiccup "^{:display-type :custom} {:a 2}")
          expr-hiccup   (let [m ^{:node/display-type :custom} {:a 2}]
                          (adorn/clj->hiccup m))
          expr-hiccup-2 (adorn/clj->hiccup :kw {:display-type :custom})]
      (t/is (re-find #"custom" (get-in str-hiccup [1 :class]))
            "Dispatch based on :type metadata should work")
      (t/is (re-find #"custom" (get-in (first str-hiccup-2) [1 :class] ""))
            "Dispatch based on :type metadata should work")
      (t/is (re-find #"custom" (get-in expr-hiccup [1 :class]))
            "Dispatch based on :type metadata should work")
      (t/is (re-find #"custom" (get-in expr-hiccup-2 [1 :class]))
            "Dispatch based on option passed in should work"))))


(t/deftest src-files
  (let [forms-parsed (parse-file "src/site/fabricate/adorn/forms.cljc")
        test-parsed  (parse-file "test/site/fabricate/adorn/forms_test.cljc")
        defaults     (atom [])]
    (t/testing "src files"
      (clojure.walk/postwalk check-class (forms/->span forms-parsed)))
    (t/testing "test files"
      (clojure.walk/postwalk check-class (forms/->span test-parsed)))))
