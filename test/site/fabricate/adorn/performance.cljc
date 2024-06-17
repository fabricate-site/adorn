(ns site.fabricate.adorn.performance
  "Performance evaluation for Adorn main operations"
  (:require [site.fabricate.adorn :as adorn]
            [site.fabricate.adorn.forms :as forms]
            [rewrite-clj.parser :as parser]
            [rewrite-clj.node :as node]
            [rewrite-clj.node.protocols :as node-proto]
            [rewrite-clj.zip :as z]
            [clojure.zip :as zip]
            [taoensso.tufte :as t]
            [clojure.walk :as walk]
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
  (time (prof/profile (dotimes [_ 50] (forms/->span core-parsed))))
  (prof/generate-diffgraph 2 1 {})
  ;; this is a recursive fn, so the constant factors are probably worth
  ;; worrying about
  (prof/profile (dotimes [_ 500] (forms/->node test-node)))
  (prof/profile (dotimes [_ 5000] (forms/->span test-node)))
  (prof/generate-diffgraph 2 3 {})
  (prof/generate-diffgraph 1 3 {})
  (prof/serve-ui 8085))


(comment
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


(comment
  ;; performance comparsion of node standardization
  ;;
  (t/profile
   {}
   (dotimes [i 10000]
     (t/p :->form (forms/->form example-node {:update-subnodes? true}))
     (t/p :->node (forms/->node example-node {:update-subnodes? true}))))
  (t/profile {}
             (dotimes [i 500]
               (t/p :->form (forms/->form core-parsed {:update-subnodes? true}))
               (t/p :->node
                    (forms/->node core-parsed {:update-subnodes? true}))))
  (prof/profile (dotimes [_ 5000]
                  (forms/->form example-node {:update-subnodes? true})))
  (prof/profile (dotimes [_ 5000]
                  (forms/->node example-node {:update-subnodes? true})))
  (prof/profile (dotimes [_ 5000]
                  (forms/->form core-parsed {:update-subnodes? true})))
  (prof/profile (dotimes [_ 500]
                  (forms/->node core-parsed {:update-subnodes? true}))))


(comment
  (t/profile
   {}
   (dotimes [_ 50]
     (t/p :parsed/walk
          (walk/walk #(if (node/node? %) (forms/->span (forms/->form % {})) %)
                     #(forms/->span (forms/->form % {}))
                     core-parsed))
     (t/p :parsed/walk-2
          (walk/walk #(if (and (node/node? %) (not (node/inner? %)))
                        (forms/token->span %)
                        %)
                     #(forms/coll->span %)
                     (forms/->form core-parsed {})))
     (t/p :parsed/span (forms/->span core-parsed))
     (t/p :parsed/prewalk
          (walk/prewalk
           #(if (node/node? %) (forms/->span (forms/->form % {})) %)
           (forms/->form core-parsed {})))
     (t/p :parsed/prewalk-2
          (walk/prewalk #(if (and (node/node? %) (not (node/inner? %)))
                           (forms/token->span %)
                           %)
                        (forms/->form core-parsed {})))
     (t/p :parsed/postwalk
          (walk/postwalk #(let [node? (node/node? %)]
                            (cond (not node?) %
                                  (and node? (not (node/inner? %)))
                                  (forms/token->span %)
                                  (and node? (node/inner? %))
                                  (forms/coll->span % {} (fn [i & args] i))
                                  :default %))
                         #_(forms/->form core-parsed {:update-subnodes? true})
                         (forms/->form core-parsed {})))))
  (walk/prewalk-demo example-node)
  ;; naive postwalk blows up the heap - unclear why, but
  ;; I think because already-converted hiccup elements
  ;; may be getting coerced
  (prof/profile
      (dotimes [i 50]
        (walk/postwalk
         (fn conv-node [n]
           (cond (and (node/node? n) (not (node/inner? n))) (forms/token->span n)
                 (and (node/node? n) (node/inner? n))
                 (forms/coll->span n {} (fn identity-inner [i & args] i))
                 :default n))
         (forms/->form core-parsed {:update-subnodes? true})
         #_(forms/->form core-parsed {}))))
  (prof/profile (dotimes [i 50]
                  (forms/->span (forms/->form core-parsed
                                              {:update-subnodes? true})
                                #_(forms/->form core-parsed {}))))
  ;; flamegraph replace for the walk-based implementation tested above:
  ;; /(clojure\.walk/walk).+;(clojure\.core/partial/fn--5839)/ $1$2
  ;; notes on performance from a transformed flamegraph:
  ;; - about 33.33% of the inner loop is spent on forms/node-attributes
  ;; - 7.48% on forms/literal-type
  ;; - 5% on node-class
  ;; this leaves a "fully optimized" perf budget of about 45.5%,
  ;; which would save 40ms if replaced with code that magically did
  ;; the same thing instantly.
  (prof/generate-diffgraph 3 4 {})
  ;; the inner loop here involves frequently calling
  ;; `forms/node-attributes`, so measuring some examples is potentially
  ;; worth it.
  (let [plain-node    (node/coerce [:abc [:def]])
        non-node-meta (node/coerce ^{:src :examples} [:abc [:def]])
        node-meta     (node/coerce ^{:node/type :example-vector} [:abc [:def]])]
    (t/profile
        {}
      (dotimes [_ 500000]
        (t/p :node-attr/empty (forms/node-attributes plain-node))
        (t/p :node-attr/non-node-meta (forms/node-attributes non-node-meta))
        (t/p :node-attr/node-meta (forms/node-attributes node-meta))
        (t/p :node-attr/literal-type (forms/literal-type plain-node)))))
  ;; a trivial optimization that would yield significant benefits here
  ;; would be streamlining the most common case: getting the literal type
  ;; of a node without metadata. if I can make detection sufficiently fast
  ;; it should be almost as fast as just calling `forms/literal-type`
  ;; alone, which would be a ~6x speedup for the most common case. Even
  ;; better would be figuring out how to avoid calling node-attributes
  ;; altogether. the preconversion step may be one way, assuming I can
  ;; maintain its speed. right now it is sufficiently fast that the
  ;; benefits of pre- conversion outweigh the costs of walking the node
  ;; tree twice. putting more work in the pre-conversion step may eliminate
  ;; that benefit.
)
