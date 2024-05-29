# Clojure vs ClojureScript

Here's a tricky question. Right now, the implementation of the forms API has started to move towards a convention of annotating the Hiccup elements with platform type information like:
```clojure
{:data-java-class "clojure.lang.Symbol"
 :data-clojure-symbol "my/sym"}
```

This opens up a can of worms because this type information will obviously not be consistent cross platform:
```clojure
{:data-js-class "cljs.core/Symbol"
 :data-cljs-symbol "my/sym"}
```

However, just using reader conditionals to dispatch to the appropriate platform defaults is too quick; Clojure (JVM) code might be parsed by `rewrite-clj` running in the browser, so it would not make sense to assign JS types to elements derived from JVM Clojure source code. The inverse might also be true: ClojureScript code might be parsed on the JVM. 

So part of the necessary complexity here is to convey to `adorn` whether the given form is Clojure or ClojureScript. Once `adorn` has this information it can populate the above metadata appropriately. When the ability to dynamically inspect types is available (e.g. reading cljs code from cljs, clj code from clj), use it. When it's not (e.g. reading cljs code from clj, clj code from cljs), default to static lookup and use a generic platform type (e.g. [Object](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object), [`java.lang.Object`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)) placeholder value for unknown types. This implies that static lookup needs to be implemented for all primitive (e.g. non-Object) types for both clj and cljs.

The multimethod-based API may allow users to extend `adorn`'s methods to arbitrary CLJS and CLJ types.

# issues with metadata-based dispatch

There's a fundamental challenge of using metadata for dispatch: it is quite literally, a different form than the one without metadata. This means that forms will be literred with metadata everywhere. 

Setting the metadata outside the function call to `clj->hiccup` doesn't change the issue:

```clojure
(= (let [v ^{:k :v} [1 2 2]]
     (clj->hiccup v)
     )

   (clj->hiccup ^{:k :v} [1 2 2])

   )
;; => true

```

So _both_ of these forms will return a metadata node.

Dispatching based on metadata is very tricky; it seems to make more sense for already-parsed forms
like maps and vectors than for unparsed strings. This dispatch struggles to display nodes with metadata attached. There's a fundamental question here:

**should the metadata that indicates a custom display type itself be displayed along with the form when converted to a span?**

I think the potential for confusion here is pretty high. this aspect of the API needs more consideration.

Here's one idea of how it might work - this requires writing the custom dispatch in a
pretty structured way.

if there is a metadata node with the `:display-type` key, the _last child_ node of that node should inherit the :display-type key from the metadata node.

this implies that the custom class annotation will live _inside_ the resulting metadata span element rather than at the top level of that form, because of the way that they are grouped together.

but that default perhaps should be questioned - just because rewrite-clj represents metadata nodes as a grouped unit doesn't mean that adorn has to. meta->span could simply return a list with the metadata element followed by the form on which metadata is set.

# Performance


## Understanding the performance gap between `node-data` and `main`
**2024-05-29**
The version of `forms/->node` on `main` does almost nothing, so it's not surprising that it has much higher performance than a version that recursively updates all subnodes. The real question is: why doesn't the recursive transformation save time when actually converting the node? Is it just because the overhead of visiting all subnodes twice washes out the potential performance benefits of converting the nodes "up front"?

A diffgraph comparing overall node conversion between the `main` and `node-data` branches will illuminate more here than just a comparison between the `->node` implementations.

When `->node` is called up front on the form to be converted, the double visitation problem disappears. This suggests that `->node` conversion could be made lazier and more efficient if rewritten to be called "just in time" when node conversion is about to happen, as long as the upper-level form data gets propagated downwards to subforms for appropriate conversion. 

The increase in self-time for the parts of the call stack that directly overlap also indicates that constant factors are slowing performance. The difference for a small data structure is (understandably) small.
