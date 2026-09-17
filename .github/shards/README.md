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

## PR runner comparisons

PRs run each API shard on `ubuntu-24.04` (x64) and `ubuntu-24.04-arm` (ARM64).
The job names and artifact suffixes identify the architecture. Push and Nightly
jobs retain their existing runner configuration.

Each PR API job publishes a phase timing table and an
`api-telemetry-shard-<shard>-<architecture>` artifact, retained for seven days:

- `machine.json`: CPU model, architecture, kernel, runner image and disk settings.
- `phases.jsonl` and `timings.json`: timestamped phase boundaries and elapsed time.
    Service-container timing includes image pulls and container launch. Java/cache
    setup, artifact waiting, artifact downloads/verification, service readiness,
    test selection, compilation and test execution are tracked separately.
- `resources.jsonl`: host CPU, memory, swap, disk and pressure counters, Docker
    resource usage, and PostgreSQL connection counts grouped by state/wait event.
    Samples are taken every ten seconds; commands have four-second timeouts and
    database sampling has a two-second statement timeout. SQL text is not collected.
- `vmstat.log` and `iostat.log`: five-second samples when those tools are installed.
    No packages are installed for diagnostics; raw `/proc` counters provide a
    fallback. The first vmstat row is an average since boot, not a five-second sample.
- `jvm-*.log`: bounded GC/safepoint logs for Maven and its child JVMs, enabled via
    `JAVA_TOOL_OPTIONS` without replacing JaCoCo's `argLine`.
- `process-time.txt`: GNU time wall/CPU time and maximum RSS for Maven and its
    descendants, excluding the separately running service containers.
- `maven.log`, `result.json`, and Surefire reports: timestamped output, Maven exit
    code and individual test results. Missing tools/services are recorded in
    `collector.log` or the corresponding resource sample.

Collection starts inside the API action, after checkout, and stops after Maven
or on earlier failure/cancellation. Diagnostic steps are best-effort and do not
override Maven's exit code. A forcibly terminated runner may not upload artifacts.
Test-execution time includes Spring context startup and fixtures; nested suite
durations must not be blindly summed. Artifact waiting measures another job's
progress, not this runner's performance. Compare repeated runs of the same commit
by phase and shard, not only whole-job duration.

Validate the helper without running API services:

        python -m unittest discover -s .github/scripts -p test_api_test_telemetry.py

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
