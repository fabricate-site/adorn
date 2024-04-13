(ns site.fabricate.adorn.parse
  (:require [rewrite-clj.node :as node :refer [tag sexpr]]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]
            [site.fabricate.adorn.forms :as forms]
            [clojure.string :as str]))


(defn node-form-meta
  "Get the metadata of the Clojure form contained in the given node.

  Returns nil if node has no metadata or can't be converted to a s-expression."
  [node]
  (if (node/sexpr-able? node) (meta (node/sexpr node))))

(defn node-info
  "Get the type of the node for display, defaulting to the rewrite-clj tag.

  If `^{:display/type :custom-type}` metadata has been set on the form, return the type.
  `:display/type` passed as an option in the options map takes precedence over metadata.
  `:display/type` can also be a map indicating how child nodes should be handled,
  in which case the `:self*` entry is used for the top-level node."
  ([node opts]
   (let [form-meta         (node-form-meta node)
         display-type      (or (get opts :display/type)
                               (get form-meta :display/type))
         self-display-type (or (when (map? display-type) (:self* display-type))
                               display-type)]
     (cond (keyword? self-display-type) self-display-type
           (ifn? self-display-type) :display/fn
           (var? self-display-type) :display/var
           :default (tag node))))
  ([node] (node-info node {})))

;; see examples of multi-arity multimethods here:
;; -
;; https://stackoverflow.com/questions/10313657/is-it-possible-to-overload-clojure-multi-methods-on-arity

(defmulti node->hiccup node-info)

(comment
  (var-get #'str)
  (resolve 'str)
  (node-info [{:a 1 :b 2}] {:display/type :dl})
  (node-info [{:a 1 :b 2}] {:display/type str}))

(defmethod node->hiccup :display/fn
  [node opts]
  (let [display-fn (get opts :display/type)] (display-fn node opts)))

;; TODO: fix this; var-get doesn't exist in cljs
(defmethod node->hiccup :display/var
  [node opts]
  (let [display-fn (var-get (get opts :display/type))] (display-fn node opts)))

(defn- span
  [class & contents]
  (apply conj [:span {:class (str "language-clojure " class)}] contents))

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



(defmethod node->hiccup :token
  [node]
  (let [node-class (atom-class node)]
    (cond (= "symbol" node-class) (forms/symbol->span node)
          (= "keyword" node-class) (forms/keyword->span node)
          :default (span (atom-class node) (forms/escape-html (str node))))))

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


;; this is a really tricky one, as it involves rewriting the expanded
;; function to resemble the input
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
                             (z/of-node (node/coerce body))
                             (fn select [zloc] (symbol? (z/sexpr zloc)))
                             (fn visit [zloc] (z/edit zloc #(get r % %)))))]
    (apply span
           (name (tag node))
           (span "dispatch" "#" (span "open-paren" "("))
           (conj (mapv node->hiccup (:children edited-node))
                 (span "close-paren" ")")))))

(defmethod node->hiccup :fn [node] (fn-node->hiccup node))

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
  (span (name (tag node)) (:prefix node) (str/replace (:s node) "\n" "") [:br]))

(-> "; a comment\n; another comment\n(+ 1 1)"
    p/parse-string-all)


(defn expr->hiccup
  "Converts the given expression into a hiccup element tokenzed into spans by the value type."
  [expr]
  (node->hiccup (rewrite-clj.node/coerce expr)))

(defn str->hiccup
  "Converts the given Clojure string into a hiccup element"
  [expr-str]
  (node->hiccup (p/parse-string expr-str)))
