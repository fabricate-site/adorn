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
             tag-method  (get-method adorn/node->hiccup node-tag)
             node-hiccup (tag-method node)]
         (update-in node-hiccup [1 :class] str " custom-type"))
       node))))

;; ultimately this likely means providing a convenience higher-order
;; function to make it easier for users to dispatch "the right way"
;; on metadata set in source code - the above example amply demonstrates that
;; it's difficult even for me, the author of the library, to do it correctly




(defmethod adorn/node->hiccup :custom
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
            "Dispatch based on option passed in should work"))))

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

(defn test-element
  [element html-conv expected-html]
  {:element       element
   :expected-html expected-html
   :result-html   (html-conv (adorn/clj->hiccup element))})

(def simple-form [:a :b :c])

(def simple-html
  "<span class=\"language-clojure vector\"><span class=\"bracket-open\">[</span><span class=\"language-clojure keyword\" data-clojure-keyword=\":a\" data-java-class=\"clojure.lang.Keyword\">:a</span><span class=\"language-clojure whitespace\"> </span><span class=\"language-clojure keyword\" data-clojure-keyword=\":b\" data-java-class=\"clojure.lang.Keyword\">:b</span><span class=\"language-clojure whitespace\"> </span><span class=\"language-clojure keyword\" data-clojure-keyword=\":c\" data-java-class=\"clojure.lang.Keyword\">:c</span><span class=\"bracket-close\">]</span></span>")

;; equivalence needs to be better defined, because the order
;; of node attributes cannot be relied upon - they're unordered maps

(t/deftest compatibility
  (require '[borkdude.html]
           #?@(:clj ['[dev.onionpancakes.chassis.core :as chassis]
                     '[hiccup.core :as hiccup] '[hiccup2.core :as hiccup2]
                     '[lambdaisland.hiccup :as li-hiccup]]
               :cljs ['[hiccups.core :as hiccups]]))
  (t/testing "equivalence of conversions"
    (doseq [converter-fn [#_#'hiccup/html #_#'hiccup2/html #'chassis/html]]
      (t/testing (str "for " converter-fn)
        (let [{:keys [result-html expected-html]}
              (test-element simple-form (var-get converter-fn) simple-html)]
          (t/is (= expected-html result-html)
                (str "HTML conversion should work for " converter-fn))))))
  (t/testing "passthrough for escaped / raw string elements"))
