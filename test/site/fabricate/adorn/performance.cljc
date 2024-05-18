(ns site.fabricate.adorn.performance
  "Performance evaluation for Adorn main operations"
  (:require [site.fabricate.adorn :as adorn]
            [site.fabricate.adorn.forms :as forms]
            [rewrite-clj.parser :as parser]
            [rewrite-clj.node :as node]
            [taoensso.tufte :as t]
            #?@(:cljs [[shadow.cljs.modern :refer [js-await]] ["fs" :as fs]])))

(def clj-core-url
  "https://raw.githubusercontent.com/clojure/clojure/clojure-1.11.3/src/clj/clojure/core.clj")
(declare clj-core)


#?(:cljs (do (def core-atom (atom nil))
             (.then (js/fetch clj-core-url)
                    #(do (let [txt-promise (.text %)]
                           (.then
                            (fn [p] (reset! core-atom p) (println p))))))))



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

(t/add-basic-println-handler! {})

(t/profile {}
           (dotimes [_ #?(:clj 50
                          :cljs 15)]
             (t/p :core-parse (parser/parse-string-all clj-core))
             (t/p :multimethod/core-parse+adorn (adorn/clj->hiccup clj-core))
             (t/p :multimethod/parsed+adorn (adorn/clj->hiccup core-parsed))
             (t/p :multimethod/sexprs+adorn (adorn/clj->hiccup core-sexprs))
             (t/p :fn/core-parse+adorn
                  (-> clj-core
                      parser/parse-string-all
                      forms/->span))
             (t/p :fn/parsed+adorn (forms/->span core-parsed))
             (t/p :fn/sexprs+adorn (forms/->span core-sexprs))))

;; mean execution time (JVM) just to parse the string is 59ms, which already
;; puts highlighting core.clj at 60 FPS outside the realm of possibility.
;; however, performance is pretty reasonable for such a huge file in the
;; grand scheme of things: 170ms on average to parse and convert a
;; 8KLoC file


(t/profile {}
  (dotimes [_ #?(:clj 50
                 :cljs 15)]
    ;; re-memoize ->span each iteration to make sure each test
    ;; is under identical conditions
    (with-redefs [forms/->span (memoize forms/->span)]
      (t/p :memoized/core-parse+adorn
           (-> clj-core
               parser/parse-string-all
               forms/->span))
      (t/p :memoized/parsed+adorn (forms/->span core-parsed))
      (t/p :memoized/sexprs+adorn (forms/->span core-sexprs)))))

;; 90th percentile performance for the pre-parsed core.clj code
;; is 16ms - which is roughly 60FPS. In cljs it's 26ms, which is
;; a little over 30FPS. That's pretty good for code that is otherwise
;; fairly unoptimized.
