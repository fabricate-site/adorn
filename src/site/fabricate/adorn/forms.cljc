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
  (first (node/children (node/coerce #'str))))

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
   :number       "number"
   :ratio        "ratio"
   :vector       "vector"
   :token        "token"
   :syntax-quote "syntax-quote"
   :list         "list"
   :quote        "quote"
   :unquote      "unquote"
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


;; the above lookup map may be "too clever" -
;; just going with (number? v) seems better
;; that platform info can be added later if it's present

(defn literal-type
  "Return the literal type for the given token node.

  Intended to be consistent across clj+cljs."
  [node]
  (cond (:lines node) :string
        (:k node) :keyword
        (boolean? (:value node)) :boolean
        (number? (:value node)) :number
        (nil? (:value node)) :nil
        (symbol? (:value node)) :symbol
        (char? (:value node)) :character
        :default :object))

(defn node-type
  [node]
  (let [t (tag node)] (if-not (= :token t) t (literal-type node))))

(def platform-classes
  "Classes for each platform"
  {:string     {:clj 'java.lang.String :cljs 'js/String}
   :multi-line {:clj 'java.lang.String :cljs 'js/String}
   :number     {:clj 'java.lang.Number :cljs 'js/Number}
   :symbol     {:clj 'clojure.lang.Symbol :cljs 'cljs.core/Symbol}
   :keyword    {:clj 'clojure.lang.Keyword :cljs 'cljs.core/Keyword}
   :var        {:clj 'clojure.lang.Var :cljs 'cljs.core/Var}
   :character  {:clj 'java.lang.Character :cljs 'js/String}
   :boolean    {:clj 'java.lang.Boolean :cljs 'js/Boolean}
   :object     {:clj 'java.lang.Object :cljs 'js/Object}
   :list       {:clj 'clojure.lang.PersistentList :cljs 'cljs.core/List}})


;; TODO: make this static with dynamic fallback
(defn node-class
  [{:keys [lang]
    :or   {lang #?(:clj :clj
                   :cljs :cljs)}
    :as   node}]
  (let [nt (node-type node)] (get-in platform-classes [nt lang])))

;; if the nodes are supposed to be optionally enriched with platform-specific
;; information, then HTML data attributes are a good way to do that.
;; (still annotate literal keywords, symbols, and vars with the values, though)


;; I think there should be a way to either augment or replace the default
;; classes
;; :classes key - augment defaults
;; :class-name key - override defaults (including language-clojure)
(defn node-attributes
  "Get the HTML element attributes for the given form.

  Allows passing through arbitrary attributes (apart from the :class attr)."
  ([{:keys [lang]
     :or   {lang #?(:clj :clj
                    :cljs :cljs)}
     :as   node} {:keys [class-name classes] :as attrs}]
   (let [nt (node-type node)
         nc (node-class node)
         node-class-name (node-html-classes nt)]
     (merge {:class (or class-name
                        (str "language-clojure"
                             " "
                             node-class-name
                             (when classes (str " " (str/join " " classes)))))}
            (when (and (not= :whitespace nt) nc)
              {(lang {:clj :data-java-class :cljs :data-js-class}) (str nc)})
            (when (= :symbol nt) {:data-clojure-symbol (str node)})
            (when (= :keyword nt) {:data-clojure-keyword (str node)})
            (when (= :var nt) {:data-clojure-var (str node)})
            (dissoc attrs :class-name :class :classes))))
  ([node] (node-attributes node {})))

(comment
  (node-type (node/string-node ["line1" "line2"]))
  (node-attributes (node/coerce (var str)) {:classes ["c1 c2"]})
  (node-class (node/coerce (var str))))

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
(declare coll->span)
(declare fn->span)

(defn token->span
  ([node attrs]
   (let [t (literal-type node)]
     (let [h [:span (node-attributes node attrs)]]
       (if (= :string t)
         (apply conj h (interpose [:br] (:lines node)))
         (conj h (str node))))))
  ([node] (token->span node {})))


(defn symbol->span
  "Generate a Hiccup <span> data structure from the given symbol.

                  Separates the namespace from the symbol, if present."
  ([node attrs]
   (let [sym      (node/sexpr node)
         sym-ns   (namespace sym)
         sym-name (name sym)
         attrs    (node-attributes node attrs)]
     (if sym-ns
       [:span attrs
        [:span {:class "language-clojure symbol-ns"} (escape-html sym-ns)] "/"
        [:span {:class "language-clojure symbol-name"} (escape-html sym-name)]]
       [:span attrs (escape-html sym-name)])))
  ([node] (symbol->span node {})))


(defn keyword->span
  "Generate a Hiccup <span> data structure from the given keyword node.

                  Separates the namespace from the keyword, if present."
  ([node attrs]
   (let [kw      (node/sexpr node)
         kw-ns   (namespace kw)
         kw-name (name kw)
         attrs   (node-attributes node attrs)]
     (if kw-ns
       [:span attrs ":"
        [:span {:class "language-clojure keyword-ns"} (escape-html kw-ns)] "/"
        [:span {:class "language-clojure keyword-name"} (escape-html kw-name)]]
       [:span attrs ":" (escape-html kw-name)])))
  ([node] (keyword->span node {})))


(defn whitespace->span
  ([node attrs]
   (let [attrs (node-attributes node attrs)] [:span attrs (:whitespace node)]))
  ([node] (whitespace->span node {})))

(defn newline->span
  ([node attrs]
   (let [attrs (node-attributes node)]
     (into [:span attrs] (repeat (count (:newlines node)) [:br]))))
  ([node] (newline->span node {})))

(def tokens
  "Spans for individual components of Clojure forms"
  {:dispatch      [:span {:class "language-clojure dispatch"} "#"]
   :caret         [:span {:class "language-clojure caret"} "^"]
   ;; is this really the best way of distinguishing quoted forms
   ;; from quotation marks?
   :quote         [:span {:class "language-clojure quote-token"} "'"]
   :syntax-quote  [:span {:class "language-clojure syntax-quote-token"} "`"]
   :unquote       [:span {:class "language-clojure unquote-token"} "~"]
   :deref         [:span {:class "language-clojure deref-token"} "@"]
   :comment       [:span {:class "language-clojure comment-start"} ";"]
   :paren/open    [:span {:class "paren-open"} "("]
   :paren/close   [:span {:class "paren-close"} ")"]
   :brace/open    [:span {:class "brace-open"} "{"]
   :brace/close   [:span {:class "brace-close"} "}"]
   :bracket/open  [:span {:class "bracket-open"} "["]
   :bracket/close [:span {:class "bracket-close"} "]"]})


(defn var->span
  ([node attrs]
   (let [var-sym-node (first (node/children node))
         var-sym      (:value var-sym-node)
         var-ns       (namespace var-sym)
         var-name     (name var-sym)
         attrs        (node-attributes node attrs)]
     (if var-ns
       [:span attrs (:dispatch tokens) "'"
        [:span {:class "language-clojure var-ns"} var-ns] "/"
        [:span {:class "language-clojure var-name"} var-name]]
       [:span attrs (:dispatch tokens) "'" (str var-sym-node)])))
  ([node] (var->span node {})))


(def coll-delimiters
  {:list   [(:paren/open tokens) (:paren/close tokens)]
   :vector [(:bracket/open tokens) (:bracket/close tokens)]
   :set    [(list (:dispatch tokens) (:brace/open tokens))
            (:brace/close tokens)]
   :map    [(:brace/open tokens) (:brace/close tokens)]})

(defn coll->span
  ([node attrs]
   (let [nt          (node-type node)
         [start end] (get coll-delimiters nt)
         attrs       (node-attributes node attrs)]
     (conj (into [:span attrs start] (map ->span (node/children node))) end)))
  ([node] (coll->span node {})))

(defn uneval->span
  ([node attrs]
   (let [attrs (node-attributes node attrs)]
     (into [:span attrs (:dispatch tokens) "_"]
           (map ->span (node/children node)))))
  ([node] (uneval->span node {})))

(defn meta->span
  ([node attrs]
   (let [attrs (node-attributes node attrs)]
     (into [:span attrs (:caret tokens)] (map ->span (node/children node)))))
  ([node] (meta->span node {})))

(defn quote->span
  ([node attrs]
   (let [attrs (node-attributes node attrs)]
     (into [:span attrs (:quote tokens)] (map ->span (node/children node)))))
  ([node] (quote->span node {})))

(defn unquote->span
  ([node attrs]
   (let [attrs (node-attributes node attrs)
         tag   (tag node)]
     (into (if (= :unquote-splicing tag)
             [:span attrs (:unquote tokens) "@"]
             [:span attrs (:unquote tokens)])
           (map ->span (node/children node)))))
  ([node] (unquote->span node {})))

(defn syntax-quote->span
  ([node attrs]
   (let [attrs (node-attributes node attrs)]
     (into [:span attrs (:syntax-quote tokens)]
           (map ->span (node/children node))))))

(defn comment->span
  ([node attrs]
   (let [attrs (node-attributes node attrs)]
     [:span attrs (:comment tokens) (:s node)]))
  ([node] (comment->span node {})))

(defn deref->span
  [node attrs]
  (let [attrs (node-attributes node attrs)]
    (into [:span attrs (:deref tokens)] (map ->span (node/children node)))))


(comment
  (into [:a {:class "something"} :b] [1 2 3 4 {5 6}])
  (into [:a {:class "something"} :b] (list 1 2 3 4 {5 6}))
  (node-type (node/coerce []))
  (coll->span (node/coerce [1 2 3 4]))
  (coll->span (node/coerce {:a 3 :b 9}))
  (list->span (node/coerce [1 2 3 4]))
  (meta->span (p/parse-string "^:meta sym"))
  (quote->span (p/parse-string "'sym") {})
  (p/parse-string "`(sym ~a)")
  (newline->span (p/parse-string "

"))
  (:s (p/parse-string ";; a comment"))
  (p/parse-string "`(sym ~@[1 2])")
  (p/parse-string "@my-atom")
  (node/children (node/coerce (list 1 1 2))))


;; if the multimethods are going to be implemented in terms of these
;; defaults,
;; then it's not clear how to "swap in" the multimethod version
;; and if they're not, then it feels like there's two separate
;; implementations

(defn ->span
  ([n attrs]
   (let [node (->node n)]
     (case (tag node)
       :fn           (fn->span node attrs)
       :meta         (meta->span node attrs)
       :multi-line   (token->span node attrs)
       :whitespace   (whitespace->span node attrs)
       :comma        (whitespace->span node
                                       (update attrs :classes conj "comma"))
       :uneval       (uneval->span node attrs)
       :vector       (coll->span node attrs)
       :token        (token->span node attrs)
       :syntax-quote (syntax-quote->span node attrs)
       :list         (coll->span node attrs)
       :var          (var->span node attrs)
       :quote        (quote->span node attrs)
       :unquote      (unquote->span node attrs)
       :deref        (deref->span node attrs)
       :comment      (comment->span node attrs)
       :regex        (token->span node attrs)
       :set          (coll->span node attrs)
       :newline      (newline->span node attrs)
       :map          (coll->span node attrs)
       :forms        (apply list (map ->span (node/children node)))
       ;; escape hatch: different arity?
       ;; or should it be a dynamic var?
       :default/value?)))
  ([n] (->span n {})))


(comment
  (p/parse-string "^{:mk :mv} [1 2 3]"))

(def form-classes
  "Mapping from form types to HTML classes

  Intended for 'higher-level' forms than rewrite-clj supports as node types"
  {:defn "defn"})

(comment
  ;; example of annotating a node with the source code type
  ;; (e.g. clj vs cljs vs bb)
  (assoc (p/parse-string "#?(:clj \"a\" :cljs \"b\")") :lang :clj)
  (assoc (p/parse-string ":abc") :lang :clj)
  (p/parse-string "#_:abc")
  (tag (p/parse-string-all "[1 2] [2 3]"))
  (node-html-class (node/coerce 3))
  (keys (.getMethodTable site.fabricate.adorn.parse/node->hiccup)))
