# Cost of the reader's inject status in SQL

Measures, with `EXPLAIN (ANALYZE, BUFFERS)`, the extra query every inject list runs to compute the reader's status,
and the filter and sort on the reader's status. Results and conclusions:
[tech design, "Measured"](../tech-design.md#measured). Rerun it to compare a cheaper marking check against
these numbers.

Needs the dev stack's `openaev-dev-pgsql` container, with the backend and pgAdmin stopped: each run recreates
`openaev_perf` as a clone of `openaev` (`CREATE DATABASE ... TEMPLATE`) and never writes to `openaev` itself.

```bash
./run.sh                          # A = 1, 10, 50 agents per inject; JIT as configured (on)
JIT=off ./run.sh                  # same, JIT off in each measured transaction
AGENTS=2 N_INJECTS=200 RUNS=1 ./run.sh   # smoke test
./check-visible.sh                # stored vs reader's status per clearance, on the last seeded A
```

| File | Role |
|---|---|
| `seed.sql` | assets (half RED), agents, atomic testings, statuses and traces |
| `queries.sh` | the measured statements; `--visible` prints the reader's status expression alone |
| `insert-cost.sql` | 100,000 traces inserted and rolled back, for the index's write cost |
| `summary.awk` | best of the runs per statement → `results-jit-<on\|off>/summary.tsv` |

The `results-jit-*` folders a run writes are not committed: the numbers are in the tech design.
