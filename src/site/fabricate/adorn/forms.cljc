(ns site.fabricate.adorn.forms
  (:require [rewrite-clj.node :as node :refer [tag sexpr]]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]
            [rewrite-clj.node.stringz]
            [clojure.string :as str]))


;; should escaping be done by this library?
;; or should it be handled by the implementation?
(def ^:dynamic *escapes*
  "Escape character substitutions for HTML."
  {\< "&lt;" \> "&gt;" \& "&amp;"})

(defn escape-html [s] (str/escape s *escapes*))



(def form-classes "Mapping from form types to HTML classes" {})

;; API design idea
;; dispatch on "type of thing" to get a Hiccup data structure

;; - string
;; - expr
;; - rewrite-clj node

;; just use cond for this unless there's a compelling reason not to.
;; this decision can be revisited later

;; Uniform API idea:
;; the outer API can be split into two levels:
;; - the "as simple as possible" core API (adorn.cljc)
;;   this has the ->hiccup dispatch fn described above and ... does it need
;;   anything else?
;; - the "form level" API - (forms.cljc)
;;   this can contain public versions of fn->hiccup
;;   should these functions _also_ dispatch on type?
;;   looking up whether something is a rewrite-clj node seems fast (protocols),
;;   so the overhead of checking may be tolerable to provide a more flexible
;;   API

;; this namespace generalizes from "node" to "form"

(defn ->node
  [i]
  (cond
    ;; if it's a node, return it as-is
    (node/node? i) i
    ;; if it's a string, parse it
    (string? i)    (p/parse-string-all i)
    ;; otherwise, assume it's a form and coerce it
    :default       (node/coerce i)))


(comment
  (node/coerce #'str)
  (first (:children (node/coerce #'str))))

(def classes
  "Default HTML class lookup for node types"
  {:var          "var"
   :fn           "fn"
   :symbol       "symbol"
   :meta         "meta"
   :multi-line   "multi-line"
   :whitespace   "whitespace"
   :comma        "comma"
   :uneval       "uneval"
   :string       "string"
   :big-int      "big-int"
   :long         "long"
   :integer      "integer"
   :ratio        "ratio"
   :vector       "vector"
   :token        "token"
   :syntax-quote "syntax-quote"
   :list         "list"
   :quote        "quote"
   :deref        "deref"
   :comment      "comment"
   :regex        "regex"
   :set          "set"
   :newline      "newline"
   :map          "map"
   :forms        "forms"})


;; here's one way that you might highlight special keywords or symbols:
;; HTML data attributes. obviously only a few types can be set as
;; data attributes, but they help convey semantic information about the code
;; most highlighting tools are syntactic only - adorn enables semantic
;; highlighting
(def data-attributes
  "HTML data attribute names for relevant information about forms"
  {:var     :data-clojure-var
   :symbol  :data-clojure-symbol
   :keyword :data-clojure-keyword
   :class   :data-java-class})



(defn symbol->span
  "Generate a Hiccup <span> data structure from the given symbol.

  Separates the namespace from the symbol, if present."
  [n]
  (let [node     (->node n)
        sym      (node/sexpr node)
        sym-ns   (namespace sym)
        sym-name (name sym)]
    (if sym-ns
      [:span
       {:class "language-clojure symbol"
        :data-clojure-symbol (escape-html (:string-value node))}
       [:span {:class "language-clojure symbol-ns"} (escape-html sym-ns)] "/"
       [:span {:class "language-clojure symbol-name"} (escape-html sym-name)]]
      [:span
       {:class "language-clojure symbol"
        :data-clojure-symbol (escape-html (:string-value node))}
       (escape-html sym-name)])))


(defn keyword->span
  "Generate a Hiccup <span> data structure from the given keyword node.

  Separates the namespace from the keyword, if present."
  [n]
  (let [node    (->node n)
        kw      (node/sexpr node)
        kw-ns   (namespace kw)
        kw-name (name kw)]
    [:span (class-name)]
    (if kw-ns
      [:span
       {:class "language-clojure keyword"
        :data-clojure-keyword (escape-html (:string-value node))} ":"
       [:span {:class "language-clojure keyword-ns"} (escape-html kw-ns)] "/"
       [:span {:class "language-clojure keyword-name"} (escape-html kw-name)]]
      [:span
       {:class "language-clojure keyword"
        :data-clojure-keyword (escape-html (:string-value node))} ":"
       (escape-html kw-name)])))



(comment
  (:value (node/coerce (var str)))
  (:string-value (first (:children (node/coerce (var str))))))

(defn var->span
  [n]
  (let [node         (->node n)
        var-sym-node (first (:children node))
        var-sym      (:value var-sym-node)
        var-ns       (namespace var-sym)
        var-name     (name var-sym)]
    [:span
     {:class (class-name node)
      :data-clojure-var (escape-html (:string-value var-sym-node))}
     (if var-ns
       (list [:span {:class (class-name "var-ns")} var-ns]
             "/"
             [:span {:class (class-name "var-name")} var-name])
       [:span {:class (class-name "var-name")} var-name])]))


;; can there be a "plain function" version of the ->hiccup fns for composite
;; forms?
;; I guess they could all just accept a map (similar to the multimethod) and
;; have
;; defaults as a fallback?

;; they are mutually recursive (a map can be in a vector and a vector can be in
;; a map)
;; so the only way to make composite forms work would be by forward declaration

(declare ->span)
(declare map->span)
(declare vector->span)
(declare list->span)
(declare fn->span)


;; if the multimethods are going to be implemented in terms of these defaults,
;; then it's not clear how to "swap in" the multimethod version
;; and if they're not, then it feels like there's two separate implementations

(def token-classes
  {rewrite_clj.node.stringz.StringNode :string
   rewrite_clj.node.keyword.KeywordNode :keyword
   clojure.lang.Symbol :symbol
   java.lang.Long :long
   java.lang.Integer :integer
   clojure.lang.BigInt :big-int
   clojure.lang.Ratio :ratio
   java.lang.Float :float
   nil :nil
   java.lang.Double :double
   java.math.BigDecimal :big-decimal
   java.lang.Class :class})

(defn token-type
  [node]
  (get token-classes
       (type node)
       (get token-classes (type (:value node)) :unknown)))

(defn node-type
  [node]
  (let [t (tag node)] (if-not (= :token t) t (token-type node))))

(defn class-name
  ([v]
   (cond (node/node? v) (str "language-clojure " (classes (tag v)))
         (string? v)    (str "language-clojure " v))))

(defn node-class
  [node]
  (if-let [v (:value node)]
    (type v)
    (when (node/sexpr-able? node) (type (node/sexpr node)))))

(defn node-attributes
  "Get the HTML element attributes for the given form"
  ([node
    {:keys [class-name] :as attrs :or {class-name (classes (node-type node))}}]
   {:class (str "language-clojure " class-name)
    :data-java-class (.getName (node-class node))})
  ([node] (node-attributes node {})))


(defn token->span [n] (let [node (->node n) node-type (token-type node)] ()))

(comment
  (type 3)
  (str (node/coerce 3))
  (token-type (node/coerce "str"))
  (str (node/coerce 'test))
  (node/type)
  (3 4)
  5)

(defn ->span
  ([n]
   (let [node (->node n)]
     (case (tag node)
       :fn           fn->span
       :meta         nil
       :multi-line   nil
       :whitespace   nil
       :comma        nil
       :uneval       nil
       :vector       vector->span
       :token        nil
       :syntax-quote nil
       :list         list->span
       :var          nil
       :quote        nil
       :deref        nil
       :comment      nil
       :regex        nil
       :set          nil
       :newline      nil
       :map          nil
       :forms        nil
       :default/value?)))
  ;; escape hatch: different arity?
  ([n node-fn] (node-fn n)))

(comment
  (require '[site.fabricate.adorn.parse])
  (keys (.getMethodTable site.fabricate.adorn.parse/node->hiccup)))
