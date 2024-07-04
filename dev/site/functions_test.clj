(ns site.functions-test
  (:require [clojure.test :as t]
            [site.functions :as site-fns]))


(t/deftest clojure-parsing
  (t/is (= "" (site-fns/md-line "")))
  (t/is (= "Here's an example of a comment hiccup form"
           (peek (site-fns/comment->element
                  [:span {:class "language-clojure comment"}
                   [:span {:class "language-clojure comment-start"} ";"]
                   "; Here's an example of a comment hiccup form\n"])))))
