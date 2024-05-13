# `adorn` design notes

## Concepts

## API

Studying `rewrite-clj`'s [protocols](https://github.com/clj-commons/rewrite-clj/blob/main/src/rewrite_clj/node/protocols.cljc) and their [implementations](https://github.com/clj-commons/rewrite-clj/blob/main/src/rewrite_clj/node/seq.cljc) has given me a few ideas for how to potentially extend the Clojure → Hiccup conversion to more than just the basic components of Clojure as a language.

Being based on `rewrite-clj` means that there likely will need to be a separation of concerns between the node-level API that operates at the level of basic sexprs and a higher-level "forms" API that expresses the intent of certain forms. At risk of stating the obvious, here are some of those higher-level forms:

- `def`
- `fn`
- `defn`
- `ns`

These aren't tagged by `rewrite-clj` as "special", even though they define the basic semantic units of the program whose code is being converted to Hiccup forms; `rewrite-clj` operates on the level of syntax. Luckily, [Clojure itself](https://github.com/clojure/core.specs.alpha) provides the tools necessary to recognize the s-expressions that carry this semantic information and distinguish them from those that don't.

These specifications can also be used for generative testing of `adorn`'s core functions.

### Idea: dispatch maps

Controlled display of subforms can be achieved if you pass through a map with specialized dispatch. Here's an example: say you want a map to be turned into a definition list instead of a regular sequence of spans.

```clojure
(clj->hiccup 
 {"term1" "definition 1"
  "term2" "definition 2"}
 {:display/type :map->dl})

;; dispatch occurs

;; result =>
(comment
  [:dl [:dt "term1"] [:dd "definition 1"]
   [:dt "term2"] [:dd "definition 2"]])


```

Now say you've got a vector of maps you want to turn into `<dl>` elements in the same way. Using metadata on each individually is tedious, but there could be another way:

```clojure

(clj->hiccup
 [{"term1" "definition 1"
   "term2" "definition 2"}]
 {:display/type :div
  :display/overrides
  {:map :map->dl}})

```

Now any map element that's a subform of the input collection will be transformed into a definition list. This offers an incredibly powerful and general way of converting Clojure into not just tokenized elements for source code display, but lots of different visual (Hiccup) representations.

So it turns out that syntax highlighting is just a special case of a much more general capability.

An alternative model for dispatch:

```clojure
(clj->hiccup
 [{"term1" "definition 1"
   "term2" "definition 2"}]
 {:display/type 
  {:map {:string :escape-string}
   :vector #'vec->list
   :*root (fn vec->dl [vec]
            (apply conj [:dl {:class "clj-map-dl"} ]
                   (map (fn [[t d]] [:dt t] [:dd (clj->hiccup d)]))))}})
```

5 ideas embedded in this small example:
1. nested dispatch for contextual display
2. using a special keyword like `:root` or `:self*` to set the display type of the top-level form
3. combining "overrides" and "display type" into a single option
4. Using a function defined inline
~~5. dispatching to a var instead of a keyword~~

### Questions with this approach
- How can the use of arbitrary inline functions be made safe on the client side?
- 

## Node-level API
Another idea: define more of the core of what `adorn` does in terms of the `rewrite-clj.node` or `rewrite-clj.zip` APIs.


### A new `Node` protocol
Maybe implement a custom `HiccupNode` protocol with an extra method - `hiccup*` - that implements all existing `Node` ops as well. That way people familiar with `rewrite-clj` can build things on top of `adorn`'s custom types. This idea needs further consideration. I don't think I understand protocols well enough yet. I also don't think they're as flexible as multimethods, so that would be a problem as well.

## Testing

Finding the right edge cases is challenging. `gen/any` almost certainly isn't generating function definitions, so I need to think about testing under a more robust set of constraints.  `core.specs.alpha` can validate functions, defns, destructure bindings, etc, but struggles to generate them. There may be two options for how to do it, both of which introduce additional dependencies:
- use spec2 (some discussion on Clojure slack about this)
- use malli (likely involves translating the core specs into malli schemas with custom generators)

Another idea: once a form is generated, I can use `rewrite-clj` to alter it for mutation testing. Start with a known good form and try to break it, then see whether conversion fails.

## Documentation

**Obviously** `adorn` must use itself to document itself.

`cljs` compatibility means that we can create a page that allows users to view the converted hiccup of any Clojure form with no server-side code. A "surprise me" button could generate a random one (using the specs above).

## Metadata

A node can _have_ metadata without _being_ a metadata node. Rewrite-clj nodes use metadata to preserve the line and column information for the source strings or files they were parsed from, when this information is present. This distinction suggests that `->node` may be able to perform the appropriate dispatch for values that only have the `:display/type` metadata set.

Maintaining this distinction may not be as easy for nodes parsed from files or strings, unless I can intercept or override part of the protocol that parses text into nodes.

### Eliding / passing metadata

A default that hides the complexity from users could be: if `display/type` is the only key in the metadata map for a given node, hide it. Otherwise, show it. The presence or absence of this key could be used to control a `:display-meta?` (or similar) key in the options map for node->meta. That way users can override the default when they want to. I think this idea is worth trying. While it makes the internals of adorn slightly more complex, it makes the interface simpler and easier to use.

Here's a potentially general-purpose way of handling this beyond just `adorn` - it would work in other contexts that rely upon passing metadata to `rewrite-clj`.

```clojure

^{:rewrite-clj/node-meta {:display/type :veclist}} [1 2 3 4]

;; results in a vector node with {:display/type :veclist} merged with its other metadata

```
This could be an escape hatch, allowing forms to pass metadata upward to rewrite-clj so they're set as attributes of the nodes and therefore elided when displaying the results.

I think this is a pragmatic choice before `adorn`'s API has fully stabilized. It could be used as a concrete example to point to in discussions of proposed changes or additions to `rewrite-clj` itself - see below for additional context.

Another idea worth considering: `:node-type` as top level metadata that automatically gets elided and applied to the form after parsing, instead of making users repeatedly go through two hops: `:rewrite-clj/node-meta`and `:display/type` - ergonomics are important to consider here.

An idea that may or may not be good: a specific reader conditional for rewrite-clj nodes that can both set metadata and `assoc` keys into the node object.

### Metadata discussion on `rewrite-clj` GitHub project

I'm not the only one who has found the default way of handling metadata in rewrite-clj cumbersome - see [Issue 115](https://github.com/clj-commons/rewrite-clj/issues/115). Sogaiu, the author of `tree-sitter-clojure` notes:

> the current tree-sitter-clojure grammar implementation puts metadata inside the things they are supposed to be attached to because i found working with rewrite-clj cumbersome wrt metadata

There has also been an idea for [node skipping](https://github.com/clj-commons/rewrite-clj/blob/main/doc/design/custom-node-skipping.adoc), which could potentially be used to "step into" metadata nodes and rewrite them using the child elements. This method would be another way of achieving a similar result. However, there are two main limitiations:
1. custom node skipping is not yet implemented; it is just an idea.
2. it relies heavily on the zipper API, which `adorn` does not currently use.
