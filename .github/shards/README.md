# API test shards

One file per shard, listing Surefire include patterns (relative to
`openaev-api/src/test/java`). The `remaining` shard in the CI matrix is a
catch-all: it runs everything **not** listed in any `api-*.txt`, so a newly added
package starts there and never goes untested.

The current layout is **14 explicit shards + catch-all 15**, shared by Core and
all Nightly API modes. Every package is assigned to an explicit shard, so the
catch-all only runs the root `io/openaev/*Test.java` classes and the packages
added since the last rebalance: it is the smallest API job, with headroom for
new packages.

The layout was repacked by `balance-api-shards.py 14` from the per-class times
of the API jobs of four green `main` runs of 15 and 16 November 2026, so that the
slowest shard decides the run length as little as possible. Shard `rest/*Test.java`
stays one unit: `*` does not cross a `/`, so the package cannot be split further
without listing files.

A shard's heap grows with the Spring contexts its classes keep in the test
context cache: `rest/exercise` once ran a shard's Surefire fork out of heap. A
class that runs on the production list of isolated tables extends
`ProductionTablesIntegrationTest` instead of declaring its own
`@DynamicPropertySource`, so it shares one context with every other such class.

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
bin-packs packages into `api-<n>.txt`. If you change the shard count, update the
`api-matrix` in `core-ci.yml` and `nightly-ci.yml` to match — the catch-all must
stay last.

Do not blindly regenerate from the current collector: it misses summaries using
`@DisplayName` and can count nested durations twice when the enclosing summary
already includes them. Check top-level summaries or Surefire XML before using
the weights. Pass `14` explicitly; the balancer's legacy default is `7`.

Always verify coverage afterwards:

    python .github/scripts/verify-test-shards.py

It fails on duplicates or an empty catch-all (which would make Surefire run zero
tests and break the JaCoCo step). It checks `*Test.java` source ownership only;
also verify generated exclusion files retain `**/*$*` to prevent duplicate
nested-class discovery.
