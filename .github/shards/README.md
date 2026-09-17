# API test shards

One file per shard, listing Surefire include patterns (relative to
`openaev-api/src/test/java`). The `remaining` shard in the CI matrix is a
catch-all: it runs everything **not** listed in any `api-*.txt`, so a newly added
package starts there and never goes untested.

The current layout is **9 explicit shards + catch-all 10**, shared by Core and
all four Nightly API modes. The catch-all intentionally retains only the three
root-level `*Test.java` files, leaving headroom for new packages.

The redistribution based on [run 35204808360](https://github.com/OpenAEV-Platform/openaev/actions/runs/35204808360)
moves `rest/inject` from shard 4 to shard 8 and top-level `config` tests from
shard 5 to shard 9. Previously unclaimed non-root test packages are split between
shards 8 and 9; shards 1, 2, 3, 6, and 7 are unchanged.

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
    python .github/scripts/balance-api-shards.py 9

The first writes `.timings.json` by parsing Surefire's
`Time elapsed: ... -- in <class>` lines out of the API job logs; the second
bin-packs packages into `api-<n>.txt`. If you change the shard count, update the
`api-matrix` in `core-ci.yml` and `nightly-ci.yml` to match — the catch-all must
stay last.

Do not blindly regenerate from the current collector: it misses summaries using
`@DisplayName` and can count nested durations twice when the enclosing summary
already includes them. Check top-level summaries or Surefire XML before using
the weights. Pass `9` explicitly; the balancer's legacy default is `7`.

Always verify coverage afterwards:

    python .github/scripts/verify-test-shards.py

It fails on duplicates or an empty catch-all (which would make Surefire run zero
tests and break the JaCoCo step). It checks `*Test.java` source ownership only;
also verify generated exclusion files retain `**/*$*` to prevent duplicate
nested-class discovery.
