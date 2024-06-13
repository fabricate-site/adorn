(ns site.fabricate.adorn.performance
  "Performance evaluation for Adorn main operations"
  (:require [site.fabricate.adorn :as adorn]
            [site.fabricate.adorn.forms :as forms]
            [rewrite-clj.parser :as parser]
            [rewrite-clj.node :as node]
            [taoensso.tufte :as t]
            #?@(:cljs [#_[shadow.cljs.modern :refer [js-await]] ["fs" :as fs]]
                :clj [[clj-async-profiler.core :as prof]])))

(def clj-core-url
  "https://raw.githubusercontent.com/clojure/clojure/clojure-1.11.3/src/clj/clojure/core.clj")
(declare clj-core)

(comment
  #?(:cljs (do (def core-atom (atom nil))
               (.then (js/fetch clj-core-url)
                      #(do (let [txt-promise (.text %)]
                             (.then
                              (fn [p] (reset! core-atom p) (println p)))))))))



;; workaround to save and cache the file locally
;; #?(:clj (spit "test-resources/clojure-core.clj" (slurp clj-core-url)))

(def clj-core
  #?(:clj (slurp #_clj-core-url "test-resources/clojure-core.clj")
     :cljs (.readFileSync fs "test-resources/clojure-core.clj" "utf8")))


(assert (string? clj-core))


(comment
  ;; async stuff I don't fully understand yet
  (let [resp
        (js-await [r (js/fetch url)] (.text r) (catch failure (prn "failed")))]
    (.text resp)))



(def core-parsed
  "All of clojure.core as a rewrite-clj FormsNode"
  (parser/parse-string-all clj-core))

(def core-sexprs "All of clojure.core as a do block" (node/sexpr core-parsed))
(def core-converted
  "A preconverted version of clojure.core"
  (forms/->node core-parsed))

(t/add-basic-println-handler! {})

(t/profile {}
           (dotimes [_ #?(:clj 25
                          :cljs 15)]
             (t/p :core-parse (parser/parse-string-all clj-core))
             (t/p :convert/parsed (forms/->node core-parsed))
             (t/p :convert/sexpr (forms/->node core-sexprs))
             (t/p :multimethod/core-parse+adorn (adorn/clj->hiccup clj-core))
             (t/p :multimethod/parsed+adorn (adorn/clj->hiccup core-parsed))
             (t/p :multimethod/converted+adorn
                  (adorn/clj->hiccup core-converted))
             (t/p :multimethod/sexprs+adorn (adorn/clj->hiccup core-sexprs))
             (t/p :fn/core-parse+adorn
                  (-> clj-core
                      parser/parse-string-all
                      forms/->span))
             (t/p :fn/parsed+adorn (forms/->span core-parsed))
             (t/p :fn/sexprs+adorn (forms/->span core-sexprs))
             (t/p :fn/converted+adorn (forms/->span core-converted))
             (t/p :sexpr/print (with-out-str (print core-sexprs)))))


(t/profile {}
           (dotimes [_ #?(:clj 15
                          :cljs 15)]
             ;; re-memoize ->span each iteration to make sure each test
             ;; is under identical conditions
             (with-redefs [forms/->span (memoize forms/->span)]
               (t/p :memoized/core-parse+adorn
                    (-> clj-core
                        parser/parse-string-all
                        forms/->span))
               (t/p :memoized/parsed+adorn (forms/->span core-parsed))
               (t/p :memoized/converted+adorn (forms/->span core-converted))
               (t/p :memoized/sexprs+adorn (forms/->span core-sexprs)))))


(comment
  (def test-node (node/coerce '(1 2 [4 5 {:a :b :aa [sym sym-2]}])))
  (def test-node-converted (forms/->node test-node))
  (prof/clear-results)
  (prof/profile (dotimes [_ 50] (forms/->span core-parsed)))
  (prof/generate-diffgraph 2 1 {})
  ;; this is a recursive fn, so the constant factors are probably worth
  ;; worrying about
  (prof/profile (dotimes [_ 500] (forms/->node test-node)))
  (prof/profile (dotimes [_ 5000] (forms/->span test-node)))
  (prof/generate-diffgraph 2 3 {})
  (prof/generate-diffgraph 1 3 {})
  (prof/profile (dotimes [_ 5000]
                  (forms/->node (node/coerce
                                 '(1 2 [4 5 {:a :b :aa [sym sym-2]}])))))
  (t/profile {}
             (dotimes [_ 1000]
               (t/p :apply-list (apply list (range 1 50000)))
               (t/p :apply-list-mapv
                    (apply list (mapv identity (range 1 50000))))
               (t/p :seq (seq (range 1 50000)))
               (t/p :doall (doall (range 1 50000)))))
  (prof/profile (dotimes [_]))
  (prof/stop)
  (prof/serve-ui 8085))


(comment
  (require '[clojure.walk :as walk])
  ;; this is the basic kind of operation that could allow for
  ;; a meaningful comparison of performance against the existing
  ;; implementation
  (defn walk-conv
    [node]
    (walk/walk #(if (node/node? %) (assoc % :type :custom) %)
               #(assoc % :type :custom :lang :clj)
               node))
  (defn mapv-conv
    [node]
    (let [rn (assoc node :type :custom)]
      (if (:children node)
        (node/replace-children rn
                               (mapv (fn update-cn [cn] (mapv-conv cn))
                                     (node/children node)))
        rn)))
  (def example-node
    (node/coerce [:abc [1 2 3 [4 5] :d :e [:f :g [:h [:i [:j [:k]]]]]]]))
  (node/node? (walk-conv example-node))
  (t/profile {}
    (dotimes [i 10000]
      (t/p :walk (walk-conv example-node))
      (t/p :mapv (mapv-conv example-node))))
  ;; the performance difference here is ENORMOUS even for a basic
  ;; operation
  (t/profile {}
    (dotimes [i 500]
      (t/p :walk (walk-conv core-parsed))
      (t/p :mapv (mapv-conv core-parsed)))))
