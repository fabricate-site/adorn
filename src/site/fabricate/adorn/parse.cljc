(ns site.fabricate.adorn.parse
  (:require
   [rewrite-clj.node :as node :refer [tag sexpr]]
   [rewrite-clj.parser :as p]
   [rewrite-clj.zip :as z]
   [clojure.repl :refer [source-fn]]
   [clojure.string :as str]))

(def ^:dynamic *escapes*
  "Escape character substitutions for HTML."
  {\< "&lt;", \> "&gt;", \& "&amp;"})

(defn escape-html [s]
  (str/escape s *escapes*))

(defn- span
  [class & contents]
  (apply conj [:span {:class (str "language-clojure " class)}] contents))

(defn node-form-meta
  "Get the metadata of the Clojure form contained in the given node.

  Returns nil if node has no metadata or can't be converted to a s-expression."
  [node]
  (if (node/sexpr-able? node)
    (meta (node/sexpr node))))

(defn node-info
  "Get the type of the node for display, defaulting to the rewrite-clj tag.

  If ^{:type :custom-type} metadata has been set on the form, return the type."
  [node]
  (let [form-meta (node-form-meta node)
        form-type (get form-meta :display/type)]
    (if (keyword? form-type) form-type (tag node))))

(defmulti node->hiccup node-info)

(defn- atom-class
  [node]
  (cond (:k node)     "keyword"
        (:lines node) "string"
        (contains? node :value) (let [v (:value node)]
                                  (cond (number? v) "number"
                                        (string? v) "string"
                                        (nil? v)    "nil"
                                        (symbol? v) "symbol"
                                        :else       (name (tag node))))
        :else         (println (tag node)
                               (keys node)
                               (sexpr node)
                               (type (sexpr node)))))

(defn sym-node->hiccup
  "Generate a Hiccup data structure from the given symbol node.

  Separates the namespace from the symbol, if present."
  [node]
  (let [sym      (node/sexpr node)
        sym-ns   (namespace sym)
        sym-name (name sym)]
    (if sym-ns
      [:span {:class "language-clojure symbol"}
       [:span {:class "language-clojure symbol-ns"} (escape-html sym-ns)] "/"
       [:span {:class "language-clojure symbol-name"}
        (escape-html sym-name)]]
      [:span {:class "language-clojure symbol"} (escape-html sym-name)])))

(defn kw-node->hiccup
  "Generate a Hiccup data structure from the given keyword node.

  Separates the namespace from the keyword, if present."
  [node]
  (let [kw      (node/sexpr node)
        kw-ns   (namespace kw)
        kw-name (name kw)]
    (if kw-ns
      [:span {:class "language-clojure keyword"} ":"
       [:span {:class "language-clojure keyword-ns"} (escape-html kw-ns)] "/"
       [:span {:class "language-clojure keyword-name"}
        (escape-html kw-name)]]
      [:span {:class "language-clojure keyword"} ":"
       (escape-html kw-name)])))

(defmethod node->hiccup :token
  [node]
  (let [node-class (atom-class node)]
    (cond (= "symbol" node-class) (sym-node->hiccup node)
          (= "keyword" node-class) (kw-node->hiccup node)
          :default (span (atom-class node) (escape-html (str node))))))

(defmethod node->hiccup :whitespace
  [node]
  (span "whitespace" (:whitespace node)))

(defmethod node->hiccup :multi-line
  [node]
  (apply span
         "string"
         (span "double-quote" "\"")
         (concat (interpose [:br] (:lines node)) [(span "double-quote" "\"")])))

(defmethod node->hiccup :map
  [node]
  (apply span
         "map"
         (span "open-brace" "{")
         (conj (mapv node->hiccup (:children node)) (span "close-brace" "}"))))

(defn- fn-list?
  [node]
  (and (= (:tag node) :list) (= 'fn* (:value (first (:children node))))))

(defn- fn-node->hiccup
  [node]
  (let [contents    (:children node)
        [_ params body] (sexpr node)
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
                             (z/edn (node/coerce body))
                             (fn select [zloc] (symbol? (z/sexpr zloc)))
                             (fn visit [zloc] (z/edit zloc #(get r % %)))))]
    (apply span
           (name (tag node))
           (span "dispatch" "#" (span "open-paren" "("))
           (conj (mapv node->hiccup (:children edited-node))
                 (span "close-paren" ")")))))

(defmethod node->hiccup :fn
  [node]
  ;; this is a really tricky one, as it involves rewriting the expanded
  ;; function to resemble the input
  (fn-node->hiccup node))

(defmethod node->hiccup :list
  [node]
  (if (fn-list? node)
    (fn-node->hiccup node)
    (apply span
           "list"
           (span "open-paren" "(")
           (conj (mapv node->hiccup (:children node))
                 (span "close-paren" ")")))))

(defmethod node->hiccup :forms
  [node]
  (apply span (name (tag node)) (map node->hiccup (:children node))))

(defmethod node->hiccup :quote
  [node]
  (apply span
         (name (tag node))
         (span "quote" "'")
         (map node->hiccup (:children node))))

(defmethod node->hiccup :syntax-quote
  [node]
  (apply span
         (name (tag node))
         (span "syntax-quote" "`")
         (map node->hiccup (:children node))))

(defmethod node->hiccup :uneval
  [node]
  (apply span
         (name (tag node))
         (span "dispatch" "#" (span "underscore" "_"))
         (map node->hiccup (:children node))))

(defmethod node->hiccup :regex
  [node]
  (span (name (tag node))
        (span "dispatch" "#")
        (map node->hiccup (:children node))))

(defmethod node->hiccup :deref
  [node]
  (apply span (name (tag node)) "@" (map node->hiccup (:children node))))

(defmethod node->hiccup :set
  [node]
  (apply span
         (name (tag node))
         (span "dispatch" "#" (span "open-brace" "{"))
         (conj (mapv node->hiccup (:children node)) (span "close-brace" "}"))))

(defmethod node->hiccup :newline
  [node]
  (repeat (count (:newlines node)) [:br {:class "language-clojure newline"}]))

(defmethod node->hiccup :vector
  [node]
  (apply span
         "vector"
         (span "open-brace" "[")
         (conj (mapv node->hiccup (:children node)) (span "close-brace" "]"))))

(defmethod node->hiccup :meta
  [node]
  (apply span
         (name (tag node))
         (span "caret" "^")
         (mapv node->hiccup (:children node))))

(defmethod node->hiccup :var
  [node]
  (span (name (tag node)) (span "var" "#'") (:value node)))

(defmethod node->hiccup :comma
  [node]
  (span (name (tag node)) (span "comma" ",")))

(defmethod node->hiccup :comment
  [node]
  (span (name (tag node))
        (:prefix node)
        (str/replace (:s node) "\n" "")
        [:br]))

(-> "; a comment\n; another comment\n(+ 1 1)"
    p/parse-string-all)


(defn expr->hiccup
  "Converts the given expression into a hiccup element tokenzed into spans by the value type."
  [expr]
  (node->hiccup (rewrite-clj.node/coerce expr)))

(defn fn->spec-form
  "Converts the given function symbol into the conformed spec for function definition"
  [fn-sym]
  (-> fn-sym
      source-fn
      read-string
      rest
      (#(clojure.spec.alpha/conform :clojure.core.specs.alpha/defn-args %))))

(defn str->hiccup
  "Converts the given Clojure string into a hiccup element"
  [expr-str]
  (node->hiccup (p/parse-string expr-str)))
