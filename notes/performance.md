---
title: Adorn's performance
---
# Adorn's performance

I would characterize Adorn as having _decent_ performance. The benchmark that guided the implementation was "render all of [`clojure.core`](https://github.com/clojure/clojure/blob/master/src/clj/clojure/core.clj) at 60FPS." With about 8,200 lines of code, I figured that it would be a large and complex enough piece of code to measure performance in a more realistically demanding context. Generally speaking, Adorn's implementation falls short of this standard. It has been observed to render an already-parsed `rewrite-clj` `FormsNode` with all of `clojure.core` in about 80 milliseconds on a M2 Mac. Parsing `clojure.core` with `rewrite-clj.parser/parse-string-all` alone takes about 50 milliseconds, so being able to parse and render it at 60FPS is not feasible. 

Adorn performs significantly better on smaller forms; I doubt that the conversion overhead will be the bottleneck for your application. However, Adorn benefits quite a bit from memoization. A "cold start" memoized version of `adorn/clj->hiccup` has been observed to render `clojure.core` in about 11 milliseconds, within the 16.67 millisecond frame budget of 60FPS. If memoization is an acceptable tradeoff for your application, and you need the performance, you can obtain it. 

You can see the benchmark setup in `test/site/fabricate/adorn/performance.cljc` for more detail on how the profiling works. Future work will automate the process of generating benchmarks to identify and catch performance regressions before they make it into the main branch.

## Ideas for improving performance

One idea that hasn't yet yielded reliable performance gains is using `clojure.walk` to perform the iteration and recursive transformation instead of the current implementation, which just uses `mapv` and mutual recursion. I haven't yet ruled it out, but I do consider it a failed experiment at this stage.

Another might be using [transients](https://clojure.org/reference/transients) to avoid allocating a bunch of intermediate vectors, but the potential for actual improvements would need to be carefully evaluated. Transient vectors cannot be recursively converted in place to persistent collections; I do not know if performing a second pass over a nested collection of transient vectors to convert all of them to persistent ones would wipe out the performance benefit of using transients.

I unfortunately must rule out other, more experimental options for improved performance, like the [ham-fisted](https://github.com/cnuernber/ham-fisted) or the [`loopr`](https://aphyr.com/posts/360-loopr-a-loop-reduction-macro-for-clojure) macro, for the sake of preserving Adorn's cross-platform compatibility.


# DOM performance

Server-side syntax highlighting is sometimes [criticized](https://macwright.com/2024/05/15/some-new-apis) for leading to excessive DOM sizes, and putting every opening and closing brace in its own `<span>` element is no exception. The README page is about __KB, including the SVG logo and fonts, uncompressed. I think this may be a lot for a relatively small page without much text, but still eligible for membership in the 512KB club.

I wrote Adorn with static sites in mind, so these nested `<span>` elements certainly contribute more to overall page size than any other factor. At the moment, my lack of experience with ClojureScript means I cannot speak to its performance in an interactive context.

However, for client-side use, there may be a different option on the horizon. CSS will eventually support a [Custom Highlight API](https://drafts.csswg.org/css-highlight-api-1/), which will provide what the specification describes as "a mechanism for styling arbitrary ranges of a document identified by script." Bramus Van Damme wrote a lengthy post that uses this API with the widely-used Prism.js library to perform syntax highlighting. 

I only have a very rough idea of what using Adorn in conjunction with this API might look like. I think it would mean defining a ClojureScript build that defines a vanilla-JS API and produces a ES6 module or script loadable via an ordinary `<script>` tag. This script could be used to provide a subset of Adorn's features on the client side, which would be called in conjunction with the Custom Highlight API to select and render ranges of text with Clojure-specific source code highlighting. 

In any case, this API is not yet fully supported by browsers and Bramus's post describes plenty of challenges along the way, so this is work for another time.
