# API test shards

One file per shard, listing Surefire include patterns (relative to
`openaev-api/src/test/java`). The `remaining` shard in the CI matrix is a
catch-all: it runs everything **not** listed in any `api-*.txt`, so a newly added
package starts there and never goes untested.

Shards are numbered rather than grouped by feature, and are balanced from
**measured** per-class runtimes rather than a heuristic. An earlier attempt
modelled cost from `@SpringBootTest` counts; refitting that model against a real
run gave ~1 min rms — the same size as the improvement being chased — because
two shards with the same number of Spring-context classes ran 4.6 min and
7.6 min. Observed times are used instead.

Roughly 3.4 min of every shard is fixed cost (JVM + Spring contexts + Maven);
only ~17 min of work is actually distributable, so adding shards has sharp
diminishing returns.

## Rebalancing

After a run finishes, harvest the real per-class times and repack:

    python .github/scripts/collect-test-timings.py <run_id>
    python .github/scripts/balance-api-shards.py 7

The first writes `.timings.json` by parsing Surefire's
`Time elapsed: ... -- in <class>` lines out of the API job logs; the second
bin-packs packages into `api-<n>.txt`. If you change the shard count, update the
`api-matrix` in `core-ci.yml` and `nightly-ci.yml` to match — the catch-all must
stay last.

Always verify coverage afterwards:

    python .github/scripts/verify-test-shards.py

It fails on duplicates or an empty catch-all (which would make Surefire run zero
tests and break the JaCoCo step).
