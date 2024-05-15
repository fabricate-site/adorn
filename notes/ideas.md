# recursive node conversion

I think that making `site.fabricate.adorn.forms/->node` recursive may be necessary to accomplish two things:
- consistent handling of form-level metadata at every level of a form (see metadata discussion in `design.md` for more context)
- consistently setting the `:lang` keyword on every subform so that they are all uniform

# performance documentation

A page that displays the results of the performance test namespace and discusses how to use adorn in a performance-sensitive manner.

**status:** not yet implemented.

## top-level performance data

Shows the table of percentiles from the most recent commit, as well as a plot of percentiles over time to track performance regressions. Offers a link to download historical data in EDN format. 

## detailed flamegraph breakdown

Also shows a flamegraph in vertical orientation detailing where the program spends most of its time. 

wall clock time per call can also be tracked over time.

## memoization example

Lastly, show and discuss how to use a memoized version of forms/->span to get good performance. 
