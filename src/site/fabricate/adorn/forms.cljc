(ns site.fabricate.adorn.forms
  "Base functions for adorn. Less extensible; higher performance."
  (:require [rewrite-clj.node :as node :refer [tag sexpr]]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]
            [rewrite-clj.node.stringz]
            [clojure.string :as str]
            [clojure.walk :as walk]))


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

;; the approach I landed on here is similar to what clj-kondo does
;; https://github.com/clj-kondo/clj-kondo/blob/master/src/clj_kondo/impl/metadata.clj
;; here's how to split the difference (maybe):
;; lift the metadata with the node prefix
;; if there's nothing left, return the inner node with the metadata applied
;; if there is something left, that may be something worth preserving in the
;; output so then just return a metadata node

(defn get-node-meta
  "Get the metadata to apply to the node itself from within the form-level metadata."
  [form-meta node-meta]
  (cond (and (keyword? form-meta) (= "node" (namespace form-meta)))
        (assoc (select-keys node-meta [:row :col :end-row :end-col])
               (keyword (name form-meta))
               true)
        (keyword? form-meta) (select-keys node-meta
                                          [:row :col :end-row :end-col])
        (map? form-meta)     (reduce-kv (fn rename-node-k [m k v]
                                          (if (and (or (keyword? k) (symbol? k))
                                                   (= "node" (namespace k)))
                                            (assoc m (keyword (name k)) v)
                                            m))
                                        (select-keys node-meta
                                                     [:row :col :end-row
                                                      :end-col])
                                        form-meta)))

(defn get-form-meta
  "Get the form-level metadata by removing any keys with the :node namespace"
  [form-meta]
  (cond (and (keyword? form-meta) (= "node" (namespace form-meta))) {}
        (keyword? form-meta) form-meta
        (map? form-meta)     (reduce-kv
                              (fn rm-node-k [m k v]
                                (if (or (and (or (keyword? k) (symbol? k))
                                             (not= "node" (namespace k)))
                                        (not (or (keyword? k) (symbol? k))))
                                  (assoc m k v)
                                  m))
                              {}
                              form-meta)))

;; this function gets called a lot, so going through the meta in one pass makes
;; more sense.
(defn split-node-metadata
  [form-meta node-meta]
  (let [kw-form?  (keyword? form-meta)
        map-form? (map? form-meta)
        node-meta {:src-info (select-keys node-meta
                                          [:row :col :end-row :end-col])}]
    (cond map-form? (reduce-kv
                     (fn update-meta [m k v]
                       (let [node-k?   (and (and (or (keyword? k) (symbol? k))
                                                 (= "node" (namespace k))))
                             renamed-k (if node-k? (keyword (name k)) k)]
                         (cond (not node-k?) (assoc-in m [:form-meta k] v)
                               node-k?       (assoc-in m
                                              [:node-meta renamed-k]
                                              v))))
                     {:form-meta {} :node-meta node-meta}
                     form-meta)
          (and kw-form? (= "node" (namespace form-meta)))
          {:form-meta {}
           :node-meta (assoc node-meta (keyword (name form-meta)) true)}
          kw-form?  {:form-meta form-meta :node-meta node-meta})))

(def src-info-keys
  "Keys used by rewrite-clj to record location info for a node"
  #{:row :col :end-row :end-col})

(def node-reserved-fields
  "Keywords used by rewrite-clj to designate node fields"
  #{:tag :format-string :wrap-length :seq-fn :children})

(defn apply-node-metadata
  "Rewrites the given node based on the type of metadata it has.

  Metadata keywords beginning with the :node namespace prefix get applied to the node;
  all other metadata remains with the node itself. If all the metadata begins with the
  :node prefix, return the inner node with that metadata applied. Otherwise, return a
  metadata node with the node-specific metadata lifted to the node and the remaining
  metadata inside the node itself.

  Non-metadata nodes are returned as-is."
  [node]
  (if (= :meta (tag node))
    (let [#_#_node-meta (meta node)
          #_#_form-meta (first (node/child-sexprs node))
          {:keys [node-meta form-meta]}
          (split-node-metadata (first (node/child-sexprs node)) (meta node))
          updated-node-meta (apply dissoc
                                   node-meta
                                   #_(get-node-meta form-meta node-meta)
                                   node-reserved-fields)
          updated-form-meta
          #_(get-form-meta form-meta)
          form-meta
          node-data
          #_(assoc (apply dissoc updated-node-meta src-info-keys)
                   :src-info
                   (select-keys updated-node-meta src-info-keys))
          updated-node-meta]
      (if (and (map? updated-form-meta) (empty? updated-form-meta))
        (with-meta (merge (peek (:children node)) node-data) updated-node-meta)
        (with-meta (assoc-in (merge node node-data)
                    [:children 0]
                    (node/coerce updated-form-meta))
                   updated-node-meta)))
    node))

(defn node-meta
  [val-meta]
  (reduce-kv (fn rename-node-k [m k v]
               (if (and (keyword? k) (= "node" (namespace k)))
                 (assoc m (keyword (name k)) v)
                 m))
             (select-keys val-meta [:row :col :end-row :end-col])
             val-meta))

(defn node-data
  ([v opts]
   (merge (node-meta (merge (meta v) opts))
          (select-keys opts [:display-type :lang])
          ;; value for if the node has been normalized
          {:converted? true}))
  ([v] (node-data v {})))

(declare ->form)

;; data model for node->form normalization

;; a form is defined as a rewrite-clj node that
;; has additional attributes as required by `adorn`,
;; and has been simplified so that those attributes
;; are applied to the nodes themselves in a uniform way.

;; this node rewriting is necessary because the additional
;; attributes are populated from the metadata applied to the
;; source of the node.

(defn get-node
  "Return a rewrite-clj node from the given value.

  If the value is already a node, return it as is.
  Parses a string into a `FormsNode` with `rewrite-clj.parser/parse-string-all`.
  Otherwise, coerces the given Clojure value using `rewrite-clj.node/coerce`."
  [value]
  (cond (node/node? value) value
        (string? value)    (p/parse-string-all value)
        :default           (node/coerce value)))

;; TODO: detect if something is already a Hiccup node, maybe
;; this would allow for already-converted elements to be child elements
;; of ones yet to be converted without worrying about coercing a Hiccup
;; element back into a node
(defn ->form
  [value
   {:keys [lang update-subnodes?]
    :or   {lang #?(:clj :clj
                   :cljs :cljs)
           update-subnodes? false}
    :as   opts}]
  (if (and (node/node? value) (:converted? value))
    value
    (let [node-value (assoc (apply-node-metadata (get-node value))
                            :lang       lang
                            :converted? true)]
      (if update-subnodes?
        (walk/walk
         (fn [v]
           (if (node/node? v)
             (assoc (apply-node-metadata value) :lang lang :converted? true)
             v))
         identity
         node-value)
        node-value))))

;; TODO: should this be called ->form instead?
;; also fix the recursion
(defn ->node
  ([i
    {:keys [lang update-subnodes?]
     :as   opts
     ;; default to platform lang if not provided
     :or   {lang #?(:clj :clj
                    :cljs :cljs)
            update-subnodes? false}}]
   (if (:converted? i)
     i
     (let [node-val (apply-node-metadata (cond
                                           ;; if it's a node, return it
                                           ;; as-is
                                           (node/node? i) i
                                           ;; if it's a string, parse it
                                           (string? i)    (p/parse-string-all i)
                                           ;; otherwise, assume it's a
                                           ;; form and coerce it
                                           :default       (node/coerce i)))
           opts     (assoc opts :lang lang)
           ;; this really shouldn't be a separate function from
           ;; apply-node-metadata
           data     (node-data i opts)]
       (merge (if (and update-subnodes? (:children node-val))
                (node/replace-children node-val
                                       (mapv (fn update-cn [cn]
                                               (->node cn opts))
                                             (node/children node-val)))
                node-val)
              data))))
  ([i] (->node i {})))


(comment
  (merge (node/coerce :abc) {:a 2})
  (->node (node/coerce [1 {:a :b}]))
  (->node (node/coerce "1"))
  (->node ":abc")
  (->node :abc {:lang :clj})
  (->node ^{:node/display-type :custom :node/attr :val}
          (p/parse-string-all ":abc")))


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
   :forms        "forms"
   :reader-macro "reader-cond"})

(comment
  (node-type (node/coerce \a))
  (node-attributes (node/coerce \a))
  (node-attributes (node/coerce true)))

(def html-class-defaults
  (reduce-kv (fn [m k v] (assoc m k (str "language-clojure " v)))
             {}
             node-html-classes))


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
   #?@(:clj [java.lang.Long       :long
             java.lang.Integer    :integer
             clojure.lang.BigInt  :big-int
             java.lang.String     :string
             clojure.lang.Ratio   :ratio
             java.lang.Float      :float
             java.lang.Double     :double
             java.math.BigDecimal :big-decimal
             java.lang.Class      :class]
       :cljs [js/Number :number
              js/String :string])})


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

(defn node-clojure-type
  [node]
  (let [t (tag node)]
    (cond (= :token t) (literal-type node)
          :default     t)))


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

(def platform-class-strs
  (reduce-kv (fn [m c-type lookup]
               (assoc m
                      c-type
                      (reduce-kv (fn [lm c-dialect cname]
                                   (assoc lm c-dialect (str cname)))
                                 {}
                                 lookup)))
             {}
             platform-classes))

(defn get-class
  [{:keys [lang] :as node}]
  (let [platform-lang #?(:clj :clj
                         :cljs :cljs)]
    (when (and (or (= lang platform-lang) (nil? lang))
               (node/sexpr-able? node)
               (= :token (node/tag node)))
      (let [node-value (node/sexpr node)
            c (class node-value)]
        (when c
          #?(:clj (.getName c)
             :cljs (str c)))))))

;; TODO: make this static with dynamic fallback
(defn node-platform-class
  [{:keys [lang]
    :or   {lang #?(:clj :clj
                   :cljs :cljs)}
    :as   node}]
  (let [nt (node-clojure-type node)]
    (or (get-in platform-class-strs [nt lang]) (get-class node))))



(comment
  (node/value "abc")
  (node-platform-class (second (node/children (p/parse-string-all
                                               "'abc 'def"))))
  (node-platform-class (second (node/children (p/parse-string-all
                                               "'abc
'def"))))
  (node-platform-class (second (node/children (p/parse-string-all
                                               "'abc 'def"))))
  (literal-type (node/coerce "abc"))
  (literal-type (node/coerce ["abc"]))
  (class (node/sexpr (node/coerce "abc")))
  (class (node/sexpr (node/coerce "abc"))))

;; if the nodes are supposed to be optionally enriched with platform-specific
;; information, then HTML data attributes are a good way to do that.
;; (still annotate literal keywords, symbols, and vars with the values,
;; though)



;; I think there should be a way to either augment or replace the default
;; classes
;; :classes key - augment defaults
;; :class-name key - override defaults (including language-clojure)

;; data model for node-attributes
;; the *clojure type* of a node/form is mapped to the HTML `class` attribute
;; the *platform type* of a node/form is mapped to the HTML `data-java-class`
;; or `data-js-class` attribute, respectively.
;; the platform type can be derived from the clojure type or detected directly
;; via introspection.

;; I hope this makes things less ambiguous.

(defn node-attributes
  "Get the HTML element attributes for the given form.

  Allows passing through arbitrary attributes (apart from the :class attr)."
  ([{:keys [lang]
     :or   {lang #?(:clj :clj
                    :cljs :cljs)}
     :as   node} {:keys [class-name classes] :as attrs}]
   (let [nt (node-clojure-type node)
         nc (node-platform-class node)
         node-platform-class-name
         (html-class-defaults nt (or (not-empty (str (name nt))) "unknown"))
         r! (transient attrs)]
     (-> r!
         (dissoc! :class-name :class :classes :lang)
         (assoc!
          :class
          (or class-name
              (if-not classes
                node-platform-class-name
                (str node-platform-class-name " " (str/join " " classes)))))
         (#(if (and (not= :whitespace nt) nc)
             (assoc! % (lang {:clj :data-java-class :cljs :data-js-class}) nc)
             %))
         (#(case nt
             :symbol  (assoc! % :data-clojure-symbol (str node))
             :keyword (assoc! % :data-clojure-keyword (str node))
             :var     (assoc! % :data-clojure-var (str node))
             %))
         persistent!)
     #_(merge {:class (or class-name
                          (str "language-clojure"
                               " "
                               node-platform-class-name
                               (when classes
                                 (str " " (str/join " " classes)))))}
              (when (and (not= :whitespace nt) nc)
                {(lang {:clj :data-java-class :cljs :data-js-class}) (str nc)})
              (when (= :symbol nt) {:data-clojure-symbol (str node)})
              (when (= :keyword nt) {:data-clojure-keyword (str node)})
              (when (= :var nt) {:data-clojure-var (str node)})
              (dissoc attrs :class-name :class :classes :lang))))
  ([node] (node-attributes node {})))

(declare ->span)
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
   (let [sym        (node/sexpr node)
         sym-ns     (namespace sym)
         sym-name   (name sym)
         span-attrs (node-attributes node attrs)]
     (if sym-ns
       [:span span-attrs
        [:span {:class "language-clojure symbol-ns"} (escape-html sym-ns)] "/"
        [:span {:class "language-clojure symbol-name"} (escape-html sym-name)]]
       [:span attrs (escape-html sym-name)])))
  ([node] (symbol->span node {})))


(defn keyword->span
  "Generate a Hiccup <span> data structure from the given keyword node.

                              Separates the namespace from the keyword, if present."
  ([node attrs]
   (let [kw         (node/sexpr node)
         kw-ns      (namespace kw)
         kw-name    (name kw)
         span-attrs (node-attributes node attrs)]
     (if kw-ns
       [:span span-attrs ":"
        [:span {:class "language-clojure keyword-ns"} (escape-html kw-ns)] "/"
        [:span {:class "language-clojure keyword-name"} (escape-html kw-name)]]
       [:span attrs ":" (escape-html kw-name)])))
  ([node] (keyword->span node {})))

(defn whitespace->span
  ([node attrs]
   (let [span-attrs (node-attributes node attrs)]
     [:span span-attrs (:whitespace node)]))
  ([node] (whitespace->span node {})))

(defn newline->span
  ([node attrs]
   (let [span-attrs (node-attributes node)]
     (into [:span span-attrs] (repeat (count (:newlines node)) [:br]))))
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
         span-attrs   (node-attributes node attrs)]
     (if var-ns
       [:span span-attrs (:dispatch tokens) "'"
        [:span {:class "language-clojure var-ns"} var-ns] "/"
        [:span {:class "language-clojure var-name"} var-name]]
       [:span span-attrs (:dispatch tokens) "'" (str var-sym-node)])))
  ([node] (var->span node {})))


(def coll-delimiters
  {:list   [(:paren/open tokens) (:paren/close tokens)]
   :vector [(:bracket/open tokens) (:bracket/close tokens)]
   :set    [(list (:dispatch tokens) (:brace/open tokens))
            (:brace/close tokens)]
   :map    [(:brace/open tokens) (:brace/close tokens)]})

(defn coll->span
  ([node attrs subform-fn]
   (let [nt          (node-clojure-type node)
         [start end] (get coll-delimiters nt)
         span-attrs  (node-attributes node attrs)]
     (conj (into [:span span-attrs start]
                 (map #(subform-fn % (select-keys attrs [:lang]))
                      (node/children node)))
           end)))
  ([node attrs] (coll->span node attrs ->span))
  ([node] (coll->span node {})))

(defn uneval->span
  ([node attrs subform-fn]
   (let [span-attrs (node-attributes node attrs)]
     (into [:span span-attrs (:dispatch tokens) "_"]
           (map #(subform-fn % (select-keys attrs [:lang]))
                (node/children node)))))
  ([node attrs] (uneval->span node attrs ->span))
  ([node] (uneval->span node {})))

(defn meta->span
  ([node attrs subform-fn]
   (let [span-attrs (node-attributes node attrs)]
     (into [:span span-attrs (:caret tokens)]
           (map #(subform-fn % (select-keys attrs [:lang]))
                (node/children node)))))
  ([node attrs] (meta->span node attrs ->span))
  ([node] (meta->span node {})))

(defn quote->span
  ([node attrs subform-fn]
   (let [span-attrs (node-attributes node attrs)]
     (into [:span span-attrs (:quote tokens)]
           (map #(subform-fn % (select-keys attrs [:lang]))
                (node/children node)))))
  ([node attrs] (quote->span node attrs ->span))
  ([node] (quote->span node {})))

(defn unquote->span
  ([node attrs subform-fn]
   (let [span-attrs (node-attributes node attrs)
         tag        (tag node)]
     (into (if (= :unquote-splicing tag)
             [:span span-attrs (:unquote tokens) "@"]
             [:span span-attrs (:unquote tokens)])
           (map #(subform-fn % (select-keys attrs [:lang]))
                (node/children node)))))
  ([node attrs] (unquote->span node attrs ->span))
  ([node] (unquote->span node {})))

(defn syntax-quote->span
  ([node attrs subform-fn]
   (let [span-attrs (node-attributes node attrs)]
     (into [:span span-attrs (:syntax-quote tokens)]
           (map #(subform-fn % (select-keys attrs [:lang]))
                (node/children node)))))
  ([node attrs] (syntax-quote->span node attrs ->span))
  ([node] (syntax-quote->span node {})))

(defn comment->span
  ([node attrs]
   (let [span-attrs (node-attributes node attrs)]
     [:span span-attrs (:comment tokens) (:s node)]))
  ([node] (comment->span node {})))

(defn deref->span
  ([node attrs subform-fn]
   (let [span-attrs (node-attributes node attrs)]
     (into [:span span-attrs (:deref tokens)]
           (map #(subform-fn % (select-keys attrs [:lang]))
                (node/children node)))))
  ([node attrs] (deref->span node attrs ->span))
  ([node] (deref->span node {})))


(defn fn->span
  ([node attrs subform-fn]
   (let [span-attrs  (node-attributes node attrs)
         contents    (node/children node)
         [_ params body] (node/sexpr node)
         r           (cond (= 0 (count params)) {}
                           (= 1 (count params)) {(first params) '%}
                           (and (= 2 (count params)) (= '& (first params)))
                           {(second params) '%&}
                           :else                (into {}
                                                      (map-indexed
                                                       (fn [ix sym]
                                                         [sym
                                                          (symbol
                                                           (str "%" (inc ix)))])
                                                       params)))
         edited-node (z/node (z/postwalk
                              (z/of-node (node/coerce body))
                              (fn select [zloc] (symbol? (z/sexpr zloc)))
                              (fn visit [zloc] (z/edit zloc #(get r % %)))))]
     (conj (into [:span span-attrs (:dispatch tokens) (:paren/open tokens)]
                 (map #(subform-fn % (select-keys attrs [:lang]))
                      (node/children edited-node)))
           (:paren/close tokens))))
  ([node attrs] (fn->span node attrs ->span))
  ([node] (fn->span node {})))

(defn reader-cond->span
  ([node attrs subform-fn]
   (let [span-attrs (node-attributes node attrs)]
     (into [:span span-attrs (:dispatch tokens)]
           (map #(subform-fn % (select-keys attrs [:lang]))
                (node/children node)))))
  ([node attrs] (reader-cond->span node attrs ->span))
  ([node] (reader-cond->span node {})))

(defn ->span
  ([n attrs subform-fn]
   ;; only convert if necessary
   (let [node (if (:converted? n) n (->node n (select-keys attrs [:lang])))]
     (case (tag node)
       :fn           (fn->span node attrs subform-fn)
       :meta         (meta->span node attrs subform-fn)
       :multi-line   (token->span node attrs)
       :whitespace   (whitespace->span node attrs)
       :comma        (whitespace->span node
                                       (update attrs :classes conj "comma"))
       :uneval       (uneval->span node attrs subform-fn)
       :vector       (coll->span node attrs subform-fn)
       :token        (token->span node attrs)
       :syntax-quote (syntax-quote->span node attrs subform-fn)
       :list         (coll->span node attrs subform-fn)
       :var          (var->span node attrs)
       :quote        (quote->span node attrs subform-fn)
       :unquote      (unquote->span node attrs subform-fn)
       :deref        (deref->span node attrs subform-fn)
       :comment      (comment->span node attrs)
       :regex        (token->span node attrs)
       :set          (coll->span node attrs subform-fn)
       :newline      (newline->span node attrs)
       :map          (coll->span node attrs subform-fn)
       :reader-macro (reader-cond->span node attrs subform-fn)
       :forms        (apply list
                            (map #(subform-fn % (select-keys attrs [:lang]))
                                 (node/children node)))
       [:span (node-attributes node {:classes ["unknown"]}) (str node)])))
  ([n attrs] (->span n attrs ->span))
  ([n] (->span n {})))


;; how to specify higher-level forms
;; needs more design consideration - is it necessary when
;; data attributes can be used to pattern match on
;; specific list forms? if there's to be an override mechanism
;; for special forms, then maybe it should be part of the multimethod
;; API to keep the forms API simple and fast.

(def form-classes
  "Mapping from form types to HTML classes

  Intended for 'higher-level' forms than rewrite-clj supports as node types"
  {:defn "defn-form" :let "let-form" :ns "ns-form"})
