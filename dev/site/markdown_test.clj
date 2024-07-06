(ns site.markdown-test
  (:require [site.fabricate.adorn :as adorn]
            [site.markdown :as markdown]
            [cybermonday.core :as md]
            [clojure.test :as t]))


(def example-blocks
  {:eval    {:block "```clojure-eval
(println \"testing\")
```" :result nil}
   :result  {:block  "```clojure-result
(+ 1 2)
```"
             :result (adorn/clj->hiccup 3)}
   :demo    {:block ""}
   :clojure ""})

(t/deftest conversion-fns
  (t/testing "code block"
    (doseq [[action {:keys [block result]}]]
      (let [block-parsed (md/parse-body block
                                        {:lower-fns {:markdown/fenced-code-block
                                                     process-code-block}})]
        (t/testing action
          (t/is (= result (process-code-block block-parsed))))))))

(t/deftest documents)
