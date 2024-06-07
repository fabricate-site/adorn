# Defining equivalence for output

Hiccup represents attributes as an unordered map. This means that different Hiccup implementations will output different strings, but they may differ only in the ordering of attributes. I do not consider the difference important when evaluating cross-library compatibility. 

This means I need to find a method of comparing two or more HTML strings that _ignores_ ordering of attributes. Possible solutions include:
- `nokogiri-diff` - a Ruby library that [implements this capability](https://github.com/postmodern/nokogiri-diff/issues/5) as of version 0.2
- `html-to-hiccup` - a [CLJC library](https://github.com/green-coder/html-to-hiccup) that parses HTML back into Hiccup data structures

Seems like `html-to-hiccup` is the preferable option for now. I'd like to avoid a Ruby dependency, which would be especially challenging to use in the context of ClojureScript testing. 
