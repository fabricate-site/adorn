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

## Documentation

**Obviously** `adorn` must use itself to document itself.

`cljs` compatibility means that we can create a page that allows users to view the converted hiccup of any Clojure form with no server-side code. A "surprise me" button could generate a random one (using the specs above).
