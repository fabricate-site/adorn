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
5. dispatching to a var instead of a keyword

### Questions with this approach
- How can the use of arbitrary inline functions be made safe on the client side?
- 

## Node-level API
Another idea: define more of the core of what `adorn` does in terms of the `rewrite-clj.node` or `rewrite-clj.zip` APIs. Maybe implement a custom `HiccupNode` implementation of the relevant protocols? That way people familiar with `rewrite-clj` can build things on top of `adorn`'s custom types.

Actually this may just mean... creating a `->hiccup` protocol and extending all the `rewrite-clj` types to support it?

## Testing

Finding the right edge cases is challenging. `gen/any` almost certainly isn't generating function definitions, so I need to think about testing under a more robust set of constraints.  `core.specs.alpha` can validate functions, defns, destructure bindings, etc, but struggles to generate them. There may be two options for how to do it, both of which introduce additional dependencies:
- use spec2 (some discussion on Clojure slack about this)
- use malli (likely involves translating the core specs into malli schemas with custom generators)

Another idea: once a form is generated, I can use `rewrite-clj` to alter it for mutation testing. Start with a known good form and try to break it, then see whether conversion fails.

## Documentation

**Obviously** `adorn` must use itself to document itself.

`cljs` compatibility means that we can create a page that allows users to view the converted hiccup of any Clojure form with no server-side code. A "surprise me" button could generate a random one (using the specs above).
