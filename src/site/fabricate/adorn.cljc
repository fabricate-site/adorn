(ns site.fabricate.adorn
  "Extensible, minimal API for adorn."
  (:require [site.fabricate.adorn.forms :as forms] ; refactor target TBD
            #_[site.fabricate.adorn.parse :as parse]
            [rewrite-clj.node :as node]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]))

(defn form-type
  "Get the type of the node for display, defaulting to the tag returned by `forms/node-type`.

  If `{:display-type :custom-type}` data has been added to the form, return the type.
  `:display-type` passed as an option in the options map takes precedence over existing node data.
  `:display-type` can also be a map indicating how child nodes should be handled,
  in which case the `:self*` entry is used for the top-level node."
  ([node opts]
   (let [display-type      (or (get opts :display-type)
                               (get node :display-type)
                               (get (meta node) :display-type)
                               (get node :type)
                               (get (meta node) :type)
                               (forms/node-type node))
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


;; TODO: figure out attribute passthrough for subnodes

(defmethod node->hiccup :display/fn
  [node opts]
  (let [display-fn (get opts :display-type)]
    (display-fn (forms/->node node) opts)))

(defmethod node->hiccup :fn
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/fn->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/fn->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :meta
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/meta->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/meta->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :multi-line
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/token->span (forms/->node node) attrs))
  ([node] (forms/token->span (forms/->node node) {})))

(defmethod node->hiccup :whitespace
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/whitespace->span (forms/->node node) attrs))
  ([node] (forms/whitespace->span (forms/->node node) {})))

(defmethod node->hiccup :comma
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/whitespace->span (forms/->node node)
                           (update attrs :classes conj "comma")))
  ([node] (forms/whitespace->span (forms/->node node) {:classes ["comma"]})))

(defmethod node->hiccup :uneval
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/uneval->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/uneval->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :vector
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/coll->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :token
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/token->span (forms/->node node) attrs))
  ([node] (forms/token->span (forms/->node node) {})))

(defmethod node->hiccup :syntax-quote
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/syntax-quote->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/syntax-quote->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :list
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/coll->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :var
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/var->span (forms/->node node) attrs))
  ([node] (forms/var->span (forms/->node node) {})))

(defmethod node->hiccup :quote
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/quote->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/quote->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :unquote
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/unquote->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/unquote->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :deref
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/deref->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/deref->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :comment
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/comment->span (forms/->node node) attrs))
  ([node] (forms/comment->span (forms/->node node) {})))

(defmethod node->hiccup :regex
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/token->span (forms/->node node) attrs))
  ([node] (forms/token->span (forms/->node node) {})))

(defmethod node->hiccup :set
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/coll->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :newline
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/newline->span (forms/->node node) attrs))
  ([node] (forms/newline->span (forms/->node node) {})))

(defmethod node->hiccup :map
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/coll->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :reader-macro
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/reader-cond->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/reader-cond->span (forms/->node node) {} node->hiccup)))

(defmethod node->hiccup :forms
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (apply list (map #(node->hiccup (forms/->node %) {}) (node/children node))))
  ([node]
   (apply list (map #(node->hiccup (forms/->node %) {}) (node/children node)))))

(defmethod node->hiccup :default
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/->span (forms/->node node) attrs node->hiccup))
  ([node] (forms/->span (forms/->node node) {} node->hiccup)))



(defn clj->hiccup
  "Convert the given Clojure string, expression, or rewrite-clj node to a Hiccup data structure.

  Uses the multimethod `site.fabricate.adorn/node->hiccup` for dispatch."
  ([src opts]
   (node->hiccup (forms/->node src (select-keys opts [:lang :update-subnodes?]))
                 opts))
  ([src] (clj->hiccup src {})))
