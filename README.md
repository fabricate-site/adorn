# adorn
Extensible conversion of Clojure code to Hiccup forms.

## Goals
- [ ] CLJC compatibility; generation of Hiccup forms using both Clojure and ClojureScript
- [ ] provide sensible defaults and an example of styling using plain CSS
  - [ ] including at least one useful Flexbox example
- [ ] provide an override mechanism for users who want to display particular forms in special ways
- [ ] provide an extension mechanism for special symbols (e.g. `def`, `defn`, `def-my-custom-def`)
- [ ] compatibility across Hiccup implementations

## Non-goals
- Conversion of Hiccup to HTML. While this conversion will be necessary in order to verify the output of `adorn`, this will strictly be a dev-time dependency. What generates HTML from the Hiccup produced by adorn is up to the user.
- Validation of output HTML.
