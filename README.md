# Adorn
## Extensible conversion of Clojure code to Hiccup forms.
Adaptation of ideas described by Michiel Borkent in ["Writing a Clojure Highlighter from Scratch"](https://blog.michielborkent.nl/writing-clojure-highlighter.html). Uses rewrite-clj to parse Clojure source code and forms, and leverages multimethods to allow for user-extensibility.

## Rationale
If you want to add syntax highlighting to your project without using a JavaScript library like [Prism](https://prismjs.com/) or [Highlight.js](https://highlightjs.org/) and you use Hiccup to generate HTML, this library might help you. I wrote it in part because I wanted more control over how my source code was displayed in HTML and CSS while developing [fabricate](https://github.com/fabricate-site/fabricate) - which this code was factored out of - than was allowed by other tools. 

More broadly, I don't think Clojure should have to rely on other language ecosystems for good display of our source code. I think it can be done better in Clojure, because Clojure code is Clojure data and we have powerful facilities for working with it, especially with the widespread use of the excellent rewrite-clj library.

## Other libraries with overlapping aims
- [Glow](https://github.com/venantius/glow) is another server-side syntax highlighting library for Clojure. It only runs on the JVM because it uses [ANTLR](https://www.antlr.org/) to parse Clojure. It also uses [Enlive](https://github.com/cgrand/enlive) instead of Hiccup for its intermediate representation of parsed Clojure code.
- [Clygments](https://github.com/bfontaine/clygments) wraps the [Pygments](https://pygments.org/) Python library, which obviously means this library introduces a dependency on Python. 

## Status
Pre-alpha. Does not yet have a clear API.

## Goals
- [ ] CLJC compatibility; generation of Hiccup forms using:
  - [ ] Clojure 
  - [ ] ClojureScript 
  - [ ] Babashka
- [ ] provide sensible defaults and an example of styling using plain CSS
  - [ ] including at least one useful Flexbox example
- [ ] provide an override mechanism for users who want to display particular forms in special ways
- [ ] provide an extension mechanism for special symbols (e.g. `def`, `defn`, `def-my-custom-def`)
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
clojure -X:test
```


## ClojureScript
Assuming that npm (globally) and shadow-cljs (locally) are installed:

To execute the project's tests:
```bash
npx shadow-cljs compile test
```


