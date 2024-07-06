(ns site.markdown
  (:require [cybermonday.core :as md]
            [site.fabricate.adorn :as adorn]))

(defn process-code-block
  "Evaluate and display the code block if it contains a directive to do so,
  otherwise highlight its Clojure code.

  Treats non-Clojure blocks as-is."
  [[_tag {:keys [language] :as attrs} contents]]
  (case language
    "clojure-eval"   (do (load-string contents) nil)
    "clojure-result" [:pre {:class "language-clojure"}
                      (adorn/clj->hiccup (load-string contents))]
    "clojure-demo"   [:div {:class "clojure-demo"}
                      [:pre {:class "language-clojure"}
                       (adorn/clj->hiccup contents)] [:hr]
                      [:pre {:class "language-clojure"}
                       (adorn/clj->hiccup (load-string contents))]]
    "clojure"        (into [:pre {:class "language-clojure"}]
                           (adorn/clj->hiccup contents))
    [:pre {:class (str "language-" language)} contents]))


(comment
  (adorn)
  (load-string "[:a 2]"))
