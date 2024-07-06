---
title: "Adorn: Extensible Conversion of Clojure code to Hiccup Forms"
---

![<img src="dev/logo.svg">](/logo-transparent.svg)

# Adorn
## Extensible conversion of Clojure code to Hiccup forms.
Adaptation of ideas described by Michiel Borkent in ["Writing a Clojure Highlighter from Scratch"](https://blog.michielborkent.nl/writing-clojure-highlighter.html). Uses rewrite-clj to parse Clojure source code and forms, and leverages multimethods to allow for user-extensibility.

## Rationale
If you want to add syntax highlighting to your project without using a JavaScript library like [Prism](https://prismjs.com/) or [Highlight.js](https://highlightjs.org/) and you use Hiccup to generate HTML, this library might help you. I wrote it in part because I wanted more control over how my source code was displayed in HTML and CSS while developing [fabricate](https://github.com/fabricate-site/fabricate) - which this code was factored out of - than was allowed by other tools. 

Most source code highlighters focus on syntax. Adorn goes further than this and allows you the ability to highlight elements based on their meaning.

More broadly, I don't think Clojure should have to rely on other language ecosystems for good display of our source code. I think it can be done better in Clojure, because Clojure code is Clojure data and we have powerful facilities for working with it, especially with the widespread use of the excellent rewrite-clj library.

## Quickstart

Adorn provides a minimal API in the `site.fabricate.adorn` namespace. Use it to produce Hiccup elements - nested `:span` vectors.

```clojure
(require
 '[site.fabricate.adorn :as adorn
   :refer [clj->hiccup]])
```

You can pass quoted Clojure forms:

```clojure
(clj->hiccup '[:vector {:map-key :map-val} symbol])
```

This results in the following data structure:

```clojure
([:span
  {:class "language-clojure vector"}
  [:span {:class "bracket-open"} "["]
  [:span
   {:class "language-clojure keyword",
    :data-java-class "clojure.lang.Keyword",
    :data-clojure-keyword ":vector"}
   ":vector"]
  [:span {:class "language-clojure whitespace"} " "]
  [:span
   {:class "language-clojure map"}
   [:span {:class "brace-open"} "{"]
   [:span
    {:class "language-clojure keyword",
     :data-java-class "clojure.lang.Keyword",
     :data-clojure-keyword ":map-key"}
    ":map-key"]
   [:span {:class "language-clojure whitespace"} " "]
   [:span
    {:class "language-clojure keyword",
     :data-java-class "clojure.lang.Keyword",
     :data-clojure-keyword ":map-val"}
    ":map-val"]
   [:span {:class "brace-close"} "}"]]
  [:span {:class "language-clojure whitespace"} " "]
  [:span
   {:class "language-clojure symbol",
    :data-java-class "clojure.lang.Symbol",
    :data-clojure-symbol "symbol"}
   "symbol"]
  [:span {:class "bracket-close"} "]"]])

```

When converted, it results in HTML like this:

<pre class="language-clojure">
<span class="language-clojure vector"><span class="bracket-open">[</span><span class="language-clojure keyword" data-java-class="clojure.lang.Keyword" data-clojure-keyword=":vector">:vector</span><span class="language-clojure whitespace"> </span><span class="language-clojure map"><span class="brace-open">{</span><span class="language-clojure keyword" data-java-class="clojure.lang.Keyword" data-clojure-keyword=":map-key">:map-key</span><span class="language-clojure whitespace"> </span><span class="language-clojure keyword" data-java-class="clojure.lang.Keyword" data-clojure-keyword=":map-val">:map-val</span><span class="brace-close">}</span></span><span class="language-clojure whitespace"> </span><span class="language-clojure symbol" data-java-class="clojure.lang.Symbol" data-clojure-symbol="symbol">symbol</span><span class="bracket-close">]</span></span>
</pre>


It also works on strings. A plain string will be assumed to contain one or more Clojure forms, and parsed with `rewrite-clj.parser/parse-string-all`.

```clojure
(clj->hiccup "[:vector {:map-key :map-val} 'symbol]")
```

And it works on  `rewrite-clj` nodes:
```clojure
(require '[rewrite-clj.parser :as p])

(clj->hiccup (p/parse-string "[:vector {:map-key :map-val} 'symbol]"))

```

## Extending adorn

`site.fabricate.adorn/clj->hiccup` uses the multimethod `site.fabricate.adorn/form->hiccup` to dispatch, which means it can be extended to new form types. 

## Form-level API

For more information, see the API docs.

## Using adorn to highlight elements

I included the resulting Hiccup form in the code examples because it demonstrates an important idea mentioned above: highlighting based on semantics. Adorn produces Hiccup elements, so if you display them by converting them to HTML you have all the power of CSS to display them as you see fit.

Say you want to assign a different color to definitions: `def`, `defn`, `defmulti`, `defprotocol`, and so on. Assigning different highlight rules to each of these terms is very difficult in other syntax highlighters - to the extent that it's possible at all.

Where appropriate, Adorn sets Clojure information, like the symbol in a form, as form-level [data attributes](https://developer.mozilla.org/en-US/docs/Learn/HTML/Howto/Use_data_attributes). So you can simply assign rules based on this attribute.

```css
span[data-clojure-symbol^="def"] {
    font-weight: 900;
}
```

Now everything that begins with `def` is covered - even if you forgot about a different one, like `clojure.test/deftest` - you'll be covered. 


## Other libraries with overlapping aims
- [Glow](https://github.com/venantius/glow) is another server-side syntax highlighting library for Clojure. It only runs on the JVM because it uses [ANTLR](https://www.antlr.org/) to parse Clojure. It also uses [Enlive](https://github.com/cgrand/enlive) instead of Hiccup for its intermediate representation of parsed Clojure code.
- [Clygments](https://github.com/bfontaine/clygments) wraps the [Pygments](https://pygments.org/) Python library, which obviously means this library introduces a dependency on Python. 

## Status
Pre-alpha. Moving towards a stable API, but does not yet have clearly defined contracts.

The `site.fabricate.adorn.forms` namespace has a fairly complete set of functions that are used as building blocks.

## Goals
- [ ] CLJC compatibility; generation of Hiccup forms using:
  - [x] Clojure 
  - [x] ClojureScript 
  - [ ] Babashka
- [ ] provide sensible defaults and an example of styling using plain CSS
  - [ ] including at least one useful Flexbox example
- [x] provide an override mechanism for users who want to display particular forms in special ways
- [x] provide an extension mechanism for special symbols (e.g. `def`, `defn`, `def-my-custom-def`)
- [ ] compatibility across Hiccup implementations

## Non-goals
- Conversion of Hiccup to HTML. While this conversion will be necessary in order to verify the output of `adorn`, this will strictly be a dev-time dependency. What generates HTML from the Hiccup produced by adorn is up to the user.
- Validation of output HTML.
- Support for other languages.

## License

Licensed under the [MIT license](https://github.com/fabricate-site/adorn/blob/main/LICENSE).

# Development

## Clojure
Assuming that the Clojure CLI is installed:

To execute the project's tests:
```bash
clojure -X:dev:test
```


## ClojureScript
Assuming that npm (globally) and shadow-cljs (locally) are installed:

To execute the project's tests:
```bash
npx shadow-cljs compile test
```


# Acknowledgements

- Thanks to [Michiel Borkent](https://github.com/borkdude) for the initial idea and excellent writeup
- Thanks to [Lee Read](https://github.com/lread), Michiel Borkent and the other contributors to `rewrite-clj` for such an excellent library, and for answering my questions about it - this library wouldn't be possible without the well-designed machinery that `rewrite-clj` provides.
- Thanks to John Newman for answering my novice questions about CLJS.
- [Oleksandr Yakushev](https://github.com/alexander-yakushev) has written a plethora of invaluable tools and information that helped me measure the performance of this code in a rigorous way.
