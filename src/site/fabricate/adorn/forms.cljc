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
  ([i
    {:keys [lang]
     :as   opts
     ;; default to platform lang if not provided
     :or   {lang #?(:clj :clj
                    :cljs :cljs)}}]
   (cond
     ;; if it's a node, return it as-is
     (node/node? i) (if (contains? i :lang) i (assoc i :lang lang))
     ;; if it's a string, parse it
     (string? i)    (assoc (p/parse-string-all i) :lang lang)
     ;; otherwise, assume it's a form and coerce it
     :default       (assoc (node/coerce i) :lang lang)))
  ([i] (->node i {})))


(comment
  (node/coerce #'str)
  (first (:children (node/coerce #'str))))

(def node-html-classes
  "Default HTML class lookup for node types"
  {:var          "var"
   :fn           "fn"
   :symbol       "symbol"
   :keyword      "keyword"
   :meta         "meta"
   :multi-line   "string multi-line"
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
   :class   :data-java-class
   :source  :data-clojure-source
   :git-sha :data-git-sha})

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

(def token-types
  ;; TODO: these need to be specified in a cljc-compatible way;
  ;; the cljs types are different
  {rewrite_clj.node.stringz.StringNode :string
   rewrite_clj.node.keyword.KeywordNode :keyword
   nil :nil
   #?(:clj clojure.lang.Symbol
      :cljs cljs.core.Symbol)
   :symbol
   #?(:clj clojure.lang.Keyword
      :cljs cljs.core.Keyword)
   :keyword
   #?(:clj java.lang.Boolean
      :cljs js/Boolean)
   :boolean
   ;; splice in platform specific types
   #?@(:clj [java.lang.Long :long java.lang.Integer :integer clojure.lang.BigInt
             :big-int java.lang.String :string clojure.lang.Ratio :ratio
             java.lang.Float :float java.lang.Double :double
             java.math.BigDecimal :big-decimal java.lang.Class :class]
       :cljs [js/Number :number js/String :string])})

(comment
  (type true)
  (symbol (.getName (type 3))))

(defn token-type
  [node]
  (get token-types (type node) (get token-types (type (:value node)) :unknown)))

(defn node-type
  [node]
  (let [t (tag node)] (if-not (= :token t) t (token-type node))))

;; TODO: make this static with dynamic fallback
(defn node-class
  [node]
  (if-let [v (:value node)]
    (type v)
    (let [nt (node-type node)]
      (cond (= :var nt)        #?(:clj clojure.lang.Var
                                  :cljs cljs.core/Var)
            (= :multi-line nt) #?(:clj java.lang.String
                                  :cljs js/String)
            (and (= :token (tag node)) (node/sexpr-able? node))
            (type (node/sexpr node))))))

(defn node-attributes
  "Get the HTML element attributes for the given form.

  Allows passing through arbitrary attributes (apart from the :class attr)."
  ([node
    {:keys [class-name]
     :as   attrs
     :or   {class-name (node-html-classes (node-type node))}}]
   (let [nt (node-type node)]
     (merge {:class (str "language-clojure " class-name)}
            #?(:clj {:data-java-class (.getName (node-class node))}
               :cljs nil)
            (when (= :symbol nt) {:data-clojure-symbol (str node)})
            (when (= :keyword nt) {:data-clojure-keyword (str node)})
            (when (= :var nt) {:data-clojure-var (str node)})
            (dissoc attrs :class-name :class))))
  ([node] (node-attributes node {})))

(defn token->span
  ([node attrs]
   (let [t (token-type node)]
     (let [h [:span (node-attributes node attrs)]]
       (if (= :string t)
         (apply conj h (interpose [:br] (:lines node)))
         (conj h (str node))))))
  ([node] (token->span node {})))

(comment
  (node-class (p/parse-string "\"abc
def
ghi\""))
  (node-class (node/coerce (var str)))
  (node-class (node/coerce #'str))
  (type 3)
  (str (node/coerce 3))
  (token-type (node/coerce "str"))
  (str (node/coerce 'test))
  (node/type)
  (3 4)
  5)




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


(defn var->span
  [n]
  (let [node         (->node n)
        var-sym-node (first (:children node))
        var-sym      (:value var-sym-node)
        var-ns       (namespace var-sym)
        var-name     (name var-sym)]
    [:span (node-attributes node)
     (if var-ns
       (list [:span {:class "language-clojure var-ns"} var-ns]
             "/"
             [:span {:class "language-clojure var-name"} var-name])
       [:span {:class "language-clojure var-name"} var-name])]))



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
       :token        token->span
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
  ;; or should it be a dynamic var?
  ([n node-fn] (node-fn n)))

(comment
  ;; example of annotating a node with the source code type
  ;; (e.g. clj vs cljs vs bb)
  (assoc (p/parse-string "#?(:clj \"a\" :cljs \"b\")") :lang :clj)
  (assoc (p/parse-string ":abc") :lang :clj)
  (require '[site.fabricate.adorn.parse])
  (keys (.getMethodTable site.fabricate.adorn.parse/node->hiccup)))
