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
                               (forms/node-clojure-type node))
         self-display-type (or (when (map? display-type) (:self* display-type))
                               display-type)]
     (cond (keyword? self-display-type) self-display-type
           (ifn? self-display-type) :display/fn
           :default (forms/node-clojure-type node))))
  ([node] (form-type node {})))


(defmulti form->hiccup
  "Extensible display of arbitrary Clojure values and rewrite-clj nodes as Hiccup elements.

  Falls back to defaults defined in `site.fabricate.adorn.forms` namespace for unimplemented values"
  form-type)


;; TODO: figure out attribute passthrough for subnodes

(defmethod form->hiccup :display/fn
  [node opts]
  (let [display-fn (get opts :display-type)]
    (display-fn (forms/->node node) opts)))

(defmethod form->hiccup :fn
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/fn->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/fn->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :meta
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/meta->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/meta->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :multi-line
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/token->span (forms/->node node) attrs))
  ([node] (forms/token->span (forms/->node node) {})))

(defmethod form->hiccup :whitespace
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/whitespace->span (forms/->node node) attrs))
  ([node] (forms/whitespace->span (forms/->node node) {})))

(defmethod form->hiccup :comma
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/whitespace->span (forms/->node node)
                           (update attrs :classes conj "comma")))
  ([node] (forms/whitespace->span (forms/->node node) {:classes ["comma"]})))

(defmethod form->hiccup :uneval
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/uneval->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/uneval->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :vector
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/coll->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :token
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/token->span (forms/->node node) attrs))
  ([node] (forms/token->span (forms/->node node) {})))

(defmethod form->hiccup :syntax-quote
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/syntax-quote->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/syntax-quote->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :list
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/coll->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :var
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/var->span (forms/->node node) attrs))
  ([node] (forms/var->span (forms/->node node) {})))

(defmethod form->hiccup :quote
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/quote->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/quote->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :unquote
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/unquote->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/unquote->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :deref
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/deref->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/deref->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :comment
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/comment->span (forms/->node node) attrs))
  ([node] (forms/comment->span (forms/->node node) {})))

(defmethod form->hiccup :regex
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/token->span (forms/->node node) attrs))
  ([node] (forms/token->span (forms/->node node) {})))

(defmethod form->hiccup :set
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/coll->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :newline
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/newline->span (forms/->node node) attrs))
  ([node] (forms/newline->span (forms/->node node) {})))

(defmethod form->hiccup :map
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/coll->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/coll->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :reader-macro
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/reader-cond->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/reader-cond->span (forms/->node node) {} form->hiccup)))

(defmethod form->hiccup :forms
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (apply list (map #(form->hiccup (forms/->node %) {}) (node/children node))))
  ([node]
   (apply list (map #(form->hiccup (forms/->node %) {}) (node/children node)))))

(defmethod form->hiccup :default
  ([node {:keys [attrs] :or {attrs {}} :as opts}]
   (forms/->span (forms/->node node) attrs form->hiccup))
  ([node] (forms/->span (forms/->node node) {} form->hiccup)))



(defn clj->hiccup
  "Convert the given Clojure string, expression, or rewrite-clj node to a Hiccup data structure.

  Uses the multimethod `site.fabricate.adorn/form->hiccup` for dispatch."
  ([src opts]
   (form->hiccup (forms/->node src (select-keys opts [:lang :update-subnodes?]))
                 opts))
  ([src] (clj->hiccup src {})))
