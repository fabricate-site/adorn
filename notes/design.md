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

## Testing

Finding the right edge cases is challenging. `gen/any` almost certainly isn't generating function definitions, so I need to think about testing under a more robust set of constraints.  `core.specs.alpha` can validate functions, defns, destructure bindings, etc, but struggles to generate them. There may be two options for how to do it, both of which introduce additional dependencies:
- use spec2 (some discussion on Clojure slack about this)
- use malli (likely involves translating the core specs into malli schemas with custom generators)

Another idea: once a form is generated, I can use `rewrite-clj` to alter it for mutation testing. Start with a known good form and try to break it, then see whether conversion fails.

## Documentation

**Obviously** `adorn` must use itself to document itself.

`cljs` compatibility means that we can create a page that allows users to view the converted hiccup of any Clojure form with no server-side code. A "surprise me" button could generate a random one (using the specs above).
