# API test shards

One file per shard, listing Surefire include patterns (relative to
`openaev-api/src/test/java`). The `remaining` shard in the CI matrix is a
catch-all: it runs everything **not** listed in any `api-*.txt`, so a newly added
package starts there and never goes untested.

Shards are numbered rather than grouped by feature, and are balanced by measured
cost — not by file count. A shard's runtime is dominated by how many Spring
context configurations it boots, not how many test files it holds: in run
30624476290 the old `remaining` shard ran 598 tests from 21 files in 4.4 min
while `misc-1` ran 368 tests from 63 files in 5.4 min.

There is a fixed ~4.4 min floor per shard (JVM + service startup), so adding
shards has sharp diminishing returns: 5 shards → ~6.8 min, 6 → ~6.4, 7 → ~6.1.

## Rebalancing

    python .github/scripts/pack-api-shards.py 6

Regenerates the split from the cost model. Update the measured times in
`.github/scripts/balance-api-shards.py` first if shard runtimes have drifted,
then re-run and copy the output into these files.

Verify coverage after any change:

    python .github/scripts/verify-test-shards.py core-ci.yml
