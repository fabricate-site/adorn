(ns site.fabricate.adorn
  "Extensible, minimal API for adorn."
  (:require [site.fabricate.adorn.forms :as forms] ; refactor target TBD
            #_[site.fabricate.adorn.parse :as parse]
            [rewrite-clj.node :as node]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]))


(defn form-type
  "Get the type of the node for display, defaulting to the tag returned by `forms/node-type`.

  If `^{:display/type :custom-type}` metadata has been set on the form, return the type.
  `:display/type` passed as an option in the options map takes precedence over metadata.
  `:display/type` can also be a map indicating how child nodes should be handled,
  in which case the `:self*` entry is used for the top-level node."
  ([node opts]
   (let [form-meta         (forms/node-form-meta node)
         display-type      (or (get opts :display/type)
                               (get form-meta :display/type))
         self-display-type (or (when (map? display-type) (:self* display-type))
                               display-type)]
     (cond (keyword? self-display-type) self-display-type
           (ifn? self-display-type) :display/fn
           :default (forms/node-type node))))
  ([node] (form-type node {})))


(defmulti node->hiccup
  "Extensible display of arbitrary Clojure values and rewrite-clj nodes as Hiccup elements.

  Falls back to defaults defined in `site.fabricate.adorn.forms` namespace for unimplemented values"
  form-type)

(defmethod node->hiccup :display/fn
  [node opts]
  (let [display-fn (get opts :display/type)] (display-fn node opts)))

(defmethod node->hiccup :fn [node _opts] (forms/fn->span node {} node->hiccup))
(defmethod node->hiccup :meta
  [node _opts]
  (forms/meta->span node {} node->hiccup))
(defmethod node->hiccup :multi-line [node _opts] (forms/token->span node {}))
(defmethod node->hiccup :whitespace
  [node _opts]
  (forms/whitespace->span node {}))
(defmethod node->hiccup :comma
  [node _opts]
  (forms/whitespace->span node (update {} :classes conj "comma")))
(defmethod node->hiccup :uneval
  [node _opts]
  (forms/uneval->span node {} node->hiccup))
(defmethod node->hiccup :vector
  [node _opts]
  (forms/coll->span node {} node->hiccup))
(defmethod node->hiccup :token [node _opts] (forms/token->span node {}))
(defmethod node->hiccup :syntax-quote
  [node _opts]
  (forms/syntax-quote->span node {} node->hiccup))
(defmethod node->hiccup :list
  [node _opts]
  (forms/coll->span node {} node->hiccup))
(defmethod node->hiccup :var [node _opts] (forms/var->span node {}))
(defmethod node->hiccup :quote
  [node _opts]
  (forms/quote->span node {} node->hiccup))
(defmethod node->hiccup :unquote
  [node _opts]
  (forms/unquote->span node {} node->hiccup))
(defmethod node->hiccup :deref
  [node _opts]
  (forms/deref->span node {} node->hiccup))
(defmethod node->hiccup :comment [node _opts] (forms/comment->span node {}))
(defmethod node->hiccup :regex [node _opts] (forms/token->span node {}))
(defmethod node->hiccup :set
  [node _opts]
  (forms/coll->span node {} node->hiccup))
(defmethod node->hiccup :newline [node _opts] (forms/newline->span node {}))
(defmethod node->hiccup :map
  [node _opts]
  (forms/coll->span node {} node->hiccup))
(defmethod node->hiccup :reader-macro
  [node _opts]
  (forms/reader-cond->span node {} node->hiccup))
(defmethod node->hiccup :forms
  [node _opts]
  (apply list (map node->hiccup (node/children node))))

(defmethod node->hiccup :default
  [node _opts]
  (forms/->span node {} node->hiccup))


(comment
  (form-type (forms/->node "^{:display/type :custom} {:a 2}")))

(defn clj->hiccup
  "Convert the given Clojure string, expression, or rewrite-clj node to a Hiccup data structure.

  Uses the multimethod `site.fabricate.adorn/node->hiccup` for dispatch."
  ([src
    {display-type :display/type
     :or          {display-type (:display/type (meta src))}
     :as          opts}]
   (let [node (let [n (forms/->node src)]
                (if-let [sm (meta src)]
                  (with-meta n sm)
                  n))]
     (node->hiccup node opts)))
  ([src] (clj->hiccup src {})))
