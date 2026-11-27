# API test shards

One file per shard, listing Surefire include patterns (relative to
`openaev-api/src/test/java`). The `remaining` shard in the CI matrix is a
catch-all: it runs everything **not** listed in any `api-*.txt`, so a newly added
package starts there and never goes untested.

The current layout is **14 packed shards, shard 15 for `architecture` and
catch-all 16**, shared by Core and all Nightly API modes. Every package is
assigned to an explicit shard, so the catch-all only runs the root
`io/openaev/*Test.java` classes and the packages added since the last rebalance:
it is the smallest API job, with headroom for new packages.

The layout was repacked by `balance-api-shards.py 14` from the per-class times of
the API jobs of ten runs of 17 November 2026, five on each of two layouts, so
that the slowest shard decides the run length as little as possible. A class
runs faster or slower depending on the shard it runs in, so the median over two
layouts weighs it better than either alone. A shard pays about 10 s for each Spring test
context its classes need, once however many of them share it, and about a minute
more for its first one. `.timings.json` holds each class's time without the start
of the context it shares, and the balancer adds a start per distinct context of a
shard, read from the test sources: packages that need the same context land in
the same shard instead of starting it again in each. A package of more than 30
classes is split into its direct classes and its subpackages, and a unit that
alone costs more than a shard's share is listed class by class, as
`rest/*Test.java` and `rest/inject/*Test.java` are: a class added later to such a
package is listed by no shard file, so it runs in the catch-all until the next
rebalance.

A shard's heap grows with the Spring contexts its classes keep in the test
context cache: `rest/exercise` once ran a shard's Surefire fork out of heap. The
statement inspector gates every tenant table in every test context, so a class
needs no `@DynamicPropertySource` for the gate and shares one context with the
other classes of its shard.

`architecture` has shard 15 to itself since
[run 44228102277](https://github.com/OpenAEV-Platform/openaev/actions/runs/44228102277),
where it ran shard 6's OpenSearch fork out of heap. Its ArchUnit rules run after
every JUnit Jupiter class of the fork and import all of `io.openaev` on top of the
Spring contexts those classes left in the cache. Alone, the import has the heap to
itself. The balancer's `ISOLATED` list keeps it there: it leaves those packages
out of the packing and writes each to its own shard file, after the packed ones.

Custom exclusion files must preserve Surefire's default `**/*$*` exclusion.
Otherwise nested classes can be discovered again in the catch-all even when
their enclosing test belongs to an explicit shard. JUnit still runs nested tests
through their enclosing class in its assigned shard.

Shards are numbered rather than grouped by feature, and are balanced from
**measured** per-class runtimes rather than a heuristic. An earlier attempt
modelled cost from `@SpringBootTest` counts; refitting that model against a real
run gave ~1 min rms — the same size as the improvement being chased — because
two shards with the same number of Spring-context classes ran 4.6 min and
7.6 min. Observed times are used instead.

Every shard pays JVM, Spring context, and Maven startup costs. Summed class
durations are workload estimates, not predictions of full CI job duration.

## Rebalancing

The existing collection and repacking commands are:

    python .github/scripts/collect-test-timings.py <run_id>
    python .github/scripts/balance-api-shards.py 14

The first writes `.timings.json` by parsing Surefire's
`Time elapsed: ... -- in <class>` lines out of the API job logs; the second
bin-packs packages into `api-1.txt` to `api-<n>.txt`, then writes one shard file
per `ISOLATED` package after them (`14` gives `api-15.txt` for `architecture`).
If you change the shard count or the `ISOLATED` list, update the
`api-matrix` in `core-ci.yml` and `nightly-ci.yml` to match — the catch-all must
stay last.

Do not blindly regenerate from the current collector: it misses summaries using
`@DisplayName`, can count nested durations twice when the enclosing summary
already includes them, and keeps in a class's time the start of the context it
shares, which the balancer then counts twice. Check top-level summaries or Surefire XML before using
the weights. Pass `14` explicitly; the balancer's legacy default is `7`.

Always verify coverage afterwards:

    python .github/scripts/verify-test-shards.py

It fails on duplicates or an empty catch-all (which would make Surefire run zero
tests and break the JaCoCo step). It checks `*Test.java` source ownership only;
also verify generated exclusion files retain `**/*$*` to prevent duplicate
nested-class discovery.
