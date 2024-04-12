(ns site.fabricate.adorn.forms-test
  (:require [site.fabricate.adorn.forms :as forms]
            [rewrite-clj.node :as node]
            [clojure.test :as t]))

(t/deftest attributes
  (t/testing "HTML attributes"
    (t/is (= {:class "language-clojure string"
              :data-java-class "java.lang.String"}
             (forms/node-attributes (node/coerce "abc"))))))




(t/deftest tokens)



(t/deftest forms)
