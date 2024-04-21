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

(defmethod node->hiccup :fn
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/fn->span node attrs node->hiccup))
  ([node] (forms/fn->span node {} node->hiccup)))

(defmethod node->hiccup :meta
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/meta->span node attrs node->hiccup))
  ([node] (forms/meta->span node {} node->hiccup)))

(defmethod node->hiccup :multi-line
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/token->span node attrs node->hiccup))
  ([node] (forms/token->span node {} node->hiccup)))

(defmethod node->hiccup :whitespace
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/whitespace->span node attrs))
  ([node] (forms/whitespace->span node {})))

(defmethod node->hiccup :comma
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/whitespace->span node
                           (update attrs :classes conj "comma")
                           node->hiccup))
  ([node] (forms/whitespace->span node {:classes ["comma"]} node->hiccup)))

(defmethod node->hiccup :uneval
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/uneval->span node attrs node->hiccup))
  ([node] (forms/uneval->span node {} node->hiccup)))

(defmethod node->hiccup :vector
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span node attrs node->hiccup))
  ([node] (forms/coll->span node {} node->hiccup)))

(defmethod node->hiccup :token
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/token->span node attrs node->hiccup))
  ([node] (forms/token->span node {} node->hiccup)))

(defmethod node->hiccup :syntax-quote
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/syntax-quote->span node attrs node->hiccup))
  ([node] (forms/syntax-quote->span node {} node->hiccup)))

(defmethod node->hiccup :list
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span node attrs node->hiccup))
  ([node] (forms/coll->span node {} node->hiccup)))

(defmethod node->hiccup :var
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/var->span node attrs node->hiccup))
  ([node] (forms/var->span node node->hiccup)))

(defmethod node->hiccup :quote
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/quote->span node attrs node->hiccup))
  ([node] (forms/quote->span node {} node->hiccup)))

(defmethod node->hiccup :unquote
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/unquote->span node attrs node->hiccup))
  ([node] (forms/unquote->span node {} node->hiccup)))

(defmethod node->hiccup :deref
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/deref->span node attrs node->hiccup))
  ([node] (forms/deref->span node {} node->hiccup)))

(defmethod node->hiccup :comment
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/comment->span node attrs))
  ([node] (forms/comment->span node {})))

(defmethod node->hiccup :regex
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/token->span node attrs))
  ([node] (forms/token->span node {})))

(defmethod node->hiccup :set
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span node attrs node->hiccup))
  ([node] (forms/coll->span node {} node->hiccup)))

(defmethod node->hiccup :newline
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/newline->span node attrs))
  ([node] (forms/newline->span node {})))

(defmethod node->hiccup :map
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span node attrs node->hiccup))
  ([node] (forms/coll->span node {} node->hiccup)))

(defmethod node->hiccup :reader-macro
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/reader-cond->span node attrs node->hiccup))
  ([node] (forms/reader-cond->span node {} node->hiccup)))

(defmethod node->hiccup :forms
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (apply list (map #(node->hiccup % {} node->hiccup) (node/children node))))
  ([node]
   (apply list (map #(node->hiccup % {} node->hiccup) (node/children node)))))

(defmethod node->hiccup :default
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/->span node attrs node->hiccup))
  ([node] (forms/->span node {} node->hiccup)))


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
