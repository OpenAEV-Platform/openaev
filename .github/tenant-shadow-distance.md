# Tenant shadow runs: distance to green

The Nightly pipeline runs the API suite twice more than the pipeline itself does, under a different
`openaev.tenant.active-tables`:

- **`shadow-prod`** arms the list `main` ships, so a regression on a table that is already live
  surfaces the night it lands.
- **`shadow-all`** arms every strict tenant table (`*`). It is the rehearsal for the day the
  activation list is retired, and the exhaustive list of what activating the rest would break.

Both also turn the fail-closed and write-attribution detectors on. Both report; neither gates.

This file is the measured answer to "how far from green are they today". It is what a reviewer of
the flip to `*` should ask for, and it is meant to be re-measured and rewritten, not left to rot.

---

## Measured on 2026-11-12, from nightly run `43145948243` (attempt 2), head `4c57c46aad`

That run checked out `4c57c46aad925737fc57b05137218e5cf70c78be`. `shadow-prod` reads
`openaev.tenant.active-tables` out of `application.properties` at the checked-out sha, and at
`4c57c46aad` that line holds **64** entries (35 at `ab2bdc4d44`, the head of the 1 October measure).
The 29 entries added since: `agents`, `attackpath_execution_collector`,
`attackpath_execution_remediation`, `attack_patterns`, `autonomous_objective_templates`,
`catalog_items`, `catalog_releases`, `collector_types`, `conditions`, `custom_domains`, `datapacks`,
`organizations`, `parameters`, `payload_identities`, `payloads`, `phishing_email_templates`,
`phishing_landing_pages`, `phishing_results`, `reporting_generations`, `reportings`,
`reporting_schedules`, `scope_variables`, `steps`, `steps_delay_queue`, `teams`, `vulnerabilities`,
`workflows`, `workflow_scope_rules`, `workflow_states`. `shadow-all` arms every strict tenant table
(`*`).

Source: the scheduled Nightly run of 2026-11-12, the ten `shadow-all` shards and the ten
Elasticsearch 8 `shadow-prod` shards, read from the run's log archive (attempt 2: every aggregate
line is stamped after that attempt started). The matrix also carries ten `elk9` `shadow-prod`
shards, left out here: they repeat the same suite on the other engine and would double-count.
Method: the log of each shard, ANSI codes stripped, read at its single Surefire aggregate line.
Each log carries exactly one aggregate line. One shard log holds NUL bytes, so every `grep` below runs
with `-a`.

### Headline

| Mode | Shards | Tests | Failures | Errors | Red tests | Red share |
|---|---|---|---|---|---|---|
| `shadow-all` | 10 | 11317 | 336 | 188 | 524 | 4.6 % |
| `shadow-prod` (elk8) | 10 | 11331 | 270 | 163 | 433 | 3.8 % |

524 red tests under `shadow-all`, 433 under `shadow-prod`: the gap between the two modes is 91
tests, so arming every strict table on top of the production list adds about a fifth to the red.

Spring context load failures: none. The 20 occurrences of `Failed to load ApplicationContext` in
each mode's logs are all the text of the shard job's own summary script (two lines per shard); no
test output carries it.

### Per shard

| Shard | `shadow-all` tests / failures / errors | `shadow-prod` tests / failures / errors |
|---|---|---|
| 1 | 751 / 14 / 0 | 751 / 14 / 0 |
| 2 | 766 / 46 / 16 | 766 / 43 / 14 |
| 3 | 1433 / 41 / 6 | 1433 / 25 / 6 |
| 4 | 1077 / 55 / 19 | 1077 / 51 / 19 |
| 5 | 1060 / 12 / 43 | 1060 / 12 / 40 |
| 6 | 1174 / 32 / 7 | 1176 / 8 / 7 |
| 7 | 1938 / 14 / 13 | 1938 / 14 / 11 |
| 8 | 1530 / 114 / 75 | 1542 / 95 / 60 |
| 9 | 782 / 5 / 4 | 782 / 5 / 1 |
| remaining | 806 / 3 / 5 | 806 / 3 / 5 |

Shard 8 carries 189 of the 524 red tests under `shadow-all` and 155 of the 433 under `shadow-prod`
(36 % in both).

### What the failures are

Counted on the per-method failure lines of the same logs (`[ERROR] <test> -- Time elapsed: ...
<<< FAILURE!` or `<<< ERROR!`, with the lines that follow). A test retried by Surefire prints one
block per attempt: 537 blocks collapse to 524 distinct (shard, test) pairs under `shadow-all`, 441
to 433 under `shadow-prod`, which are the aggregate totals. The families below count those distinct
pairs, each assigned to the first family whose text appears in its first twelve lines.

| Primary cause | `shadow-all` | `shadow-prod` |
|---|---|---|
| The fail-closed detector refused an unscoped read (assertion text `Fail-closed: production code read an active tenant table`) | 0 | 0 |
| Everything else | 524 | 433 |

Under a shadow mode the fail-closed gate reports its signatures in `failclosed-shadow.txt` and
fails no test (`FailClosedGateExtension.failsPerTest`), so its refusals do not appear as failures.
The detector's own stdout marker, `[FAILCLOSED]`, is on 5451 log lines under `shadow-all` and 6778
under `shadow-prod`: one line per unscoped statement, not per test.

The 524 and 433 split as:

| Family | `shadow-all` | `shadow-prod` |
|---|---|---|
| HTTP status mismatch (`Status expected`, `Range for response status`) | 96 | 30 |
| JSON content mismatch (`JsonAssertError`, `JSON path`, `JSON documents are different`) | 73 | 83 |
| Empty `Optional` or iterator (`NoSuchElementException`) | 71 | 64 |
| Write-attribution detector (`Write-attribution: production code wrote a tenant_id`) | 70 | 70 |
| Row not found by id (`ObjectRetrievalFailureException`, `ObjectNotFoundException`) | 59 | 58 |
| AssertJ or JUnit assertion (`Expecting`, `expected: <..>`) | 51 | 42 |
| Optimistic locking (`ObjectOptimisticLockingFailureException`, `StaleObjectStateException`) | 26 | 12 |
| Unique or integrity violation (`DataIntegrityViolationException`) | 11 | 11 |
| Not matched by the families above (mostly empty `AssertionError` messages) | 67 | 63 |

The write-attribution detector (the second one the shadow runs turn on) fails 70 tests in each
mode, the same 70 on the production list.

None of the database shapes that dominated the 1 October measure appears in these logs. Occurrences of
`violates unique constraint "<name>"` across the ten shards of each mode:

| Constraint | `shadow-all` | `shadow-prod` | Likely cause |
|---|---|---|---|
| `injectors_pkey` | 30 | 30 | Injector registration re-inserting |
| `executors_pkey` | 25 | 25 | Executor registration re-inserting |

These are the only two constraints named, with 55 occurrences in each mode. The other recurring
shapes, occurrences in the shard logs of each mode:

| Shape | `shadow-all` | `shadow-prod` |
|---|---|---|
| `No row with the given identifier exists` (entities: `Payload` 132 in both modes; `Exercise` 84 under `shadow-all`; `Asset` 39 under `shadow-prod`) | 222 | 174 |
| `ObjectOptimisticLockingFailureException` | 39 | 23 |
| `OptimisticLockException` | 5 | 5 |
| `StaleObjectStateException` | 28 | 14 |
| `Injector not found after initialization` | 2 | 2 |
| `JSON documents are different` | 18 | 102 |
| `Status expected:<200>`, actual status 404 | 144 | 18 |
| `Status expected:<200>`, actual status 400 | 0 | 2 |
| `Status expected:<400>`, actual status 404 | 6 | 2 |
| `Status expected:<400>`, actual status 200 | 2 | 2 |
| `Status expected:<403>`, actual status 404 / 200 | 4 / 0 | 2 / 4 |
| `Range for response status`, actual 404 / 422 | 56 / 0 | 24 / 10 |

Other exception names in the raw logs (occurrences under `shadow-all` and `shadow-prod`): `NoSuchElementException`
(88 and 73), `ElementNotFoundException` (124 and 38), `HibernateExceptionTranslator` frames (164
and 162), and `ConnectorError` (74 in both modes: the `workToReceived` and `workToProcessed` calls
of the coverage connector, 37 each).

The 404 answers to a request that expects 200 are where the two modes differ most: 144 occurrences
under `shadow-all` against 18 under `shadow-prod`.

### The 20 test names with the most red

From the per-class lines `Tests run: N, Failures: F, Errors: E, ... <<< FAILURE! -- in <name>`,
F+E summed by name across shards, names as Surefire prints them (some are display names; the
second column is the tests those lines ran). Ties at the 20th value are cut as `sort -rn` leaves them.
The sum over all per-class lines is 537 under `shadow-all` and 441 under `shadow-prod`, 13 and 8
above the aggregates: each excess is a (shard, test) pair whose failure block is printed twice. The 20 names below sum to
251 of the 524 and 224 of the 433.

`shadow-all`:

| Red | Run | Name |
|---|---|---|
| 46 | 46 | Finding Processing Handling |
| 26 | 30 | io.openaev.service.attackpath.AttackPathGraphServiceTest |
| 19 | 20 | When getting options for inject targets |
| 14 | 14 | An inject of another tenant is refused before the handler runs |
| 13 | 16 | Injector Contract search tests with different user types for RBAC |
| 12 | 12 | An agent of a non-default tenant, on the prefixed route |
| 11 | 16 | When deleting an injector contract |
| 11 | 11 | When filtering with tags |
| 10 | 10 | A caller with the runtime capability in the default tenant and in two others |
| 10 | 10 | A caller with the runtime capability in two tenants, none in the default one |
| 10 | 15 | requestForInstance |
| 9 | 9 | With default export options |
| 8 | 8 | With actual results |
| 8 | 9 | With enabled configuration |
| 8 | 10 | io.openaev.api.attackpath.AttackPathKillChainGraphTest |
| 8 | 10 | io.openaev.integration.impl.executor.CalderaExecutorIntegrationTest |
| 7 | 12 | Credential references |
| 7 | 7 | Fetch and update InjectExpectations from collectors |
| 7 | 7 | With AND filter group operator |
| 7 | 18 | attack path Phase A: create EXECUTION rows |

`shadow-prod`:

| Red | Run | Name |
|---|---|---|
| 46 | 46 | Finding Processing Handling |
| 26 | 30 | io.openaev.service.attackpath.AttackPathGraphServiceTest |
| 16 | 18 | When getting options for inject targets |
| 12 | 12 | An agent of a non-default tenant, on the prefixed route |
| 11 | 11 | When filtering with tags |
| 10 | 10 | A caller with the runtime capability in the default tenant and in two others |
| 10 | 10 | A caller with the runtime capability in two tenants, none in the default one |
| 10 | 15 | requestForInstance |
| 8 | 9 | With enabled configuration |
| 8 | 10 | io.openaev.api.attackpath.AttackPathKillChainGraphTest |
| 8 | 10 | io.openaev.integration.impl.executor.CalderaExecutorIntegrationTest |
| 7 | 16 | When deleting an injector contract |
| 7 | 7 | With AND filter group operator |
| 7 | 8 | With actual results |
| 7 | 18 | attack path Phase A: create EXECUTION rows |
| 7 | 9 | io.openaev.integration.impl.executor.SentinelOneExecutorIntegrationTest |
| 6 | 6 | A caller of the default tenant |
| 6 | 6 | attack path execution detail - security platforms (A1) |
| 6 | 6 | attack path: a chaining run drives the ingestion (run-level wiring) |
| 6 | 8 | io.openaev.integration.impl.executor.CrowdStrikeExecutorIntegrationTest |

### Against the 1 October measure

The 1 October measure (run `36809639051`, head `ab2bdc4d44`, 35 active tables) read 7624 tests and
28 % red under `shadow-all`, 7642 tests and 26 % red under `shadow-prod`; the two modes sat at the
same distance from green.

| | 1 October `shadow-all` | now `shadow-all` | 1 October `shadow-prod` | now `shadow-prod` |
|---|---|---|---|---|
| Active tables armed | `*` | `*` | 35 | 64 |
| Tests | 7624 | 11317 | 7642 | 11331 |
| Red tests (failures + errors) | 2129 | 524 | 1961 | 433 |
| Red share | 28 % | 4.6 % | 26 % | 3.8 % |
| Refusals by the fail-closed detector (primary cause) | 763 | 0 | 1157 | 0 |
| `domains_domain_name_tenant_key` | 2484 | 0 | 2484 | 0 |
| `collector_types_name_tenant_unique` | 360 | 0 | 0 | 0 |
| `tag_name_tenant_unique` | 70 | 0 | 70 | 0 |
| `injectors_pkey` | 174 | 30 | 209 | 30 |
| `executors_pkey` | 53 | 25 | 53 | 25 |
| `OptimisticLockException` | about 120 | 5 | not measured | 5 |
| `Injector not found after initialization` | 107 | 2 | not measured | 2 |
| `Failed to load ApplicationContext` occurrences | 20 | 20 (all in the shard job's summary script, 0 from tests) | 20 | 20 (same) |

Down: the red count, the red share and every named shape. The suite grew by 3693 tests under `shadow-all` and 3689 under
`shadow-prod`, and the red fell by 1605 and 1528. Red per shard (1 October, now), `shadow-all`:
shard 1 129 to 14, 2 229 to 62, 3 313 to 47, 4 255 to 74, 5 250 to 55, 6 327 to 39, 7 152 to 27, 8
268 to 189, 9 86 to 9, remaining 120 to 8; `shadow-prod`: 1 122 to 14, 2 218 to 57, 3 265 to 31, 4
249 to 70, 5 226 to 52, 6 306 to 15, 7 126 to 25, 8 257 to 155, 9 85 to 6, remaining 107 to 8.
Shard 8 falls the least.

Up: the suite (about 3.7 thousand tests) and the production list (29 tables). None of the measured
failure shapes went up. The gap between the two modes narrowed from 168 red tests (`shadow-all`
redder, 2129 against 1961) to 91 (524 against 433), with 29 more tables armed in `shadow-prod`.

The `OptimisticLockException` row counts that exact string. With
`ObjectOptimisticLockingFailureException` the occurrences are 5 + 39 under `shadow-all` and 5 + 23
under `shadow-prod`.

### What this means for retiring the activation list

1. The distance to green is 524 red tests of 11317 under `shadow-all`, and 433 of 11331 under the
   list `main` ships. No single database shape accounts for hundreds of them: the two named
   constraints total 55 occurrences, and the largest failure family is 96 tests.
2. 432 of the 524 red tests under `shadow-all` are also red under the production list (the same
   shard and test name), so most of the work sits on the list `main` ships, not on the flip. The 92
   that only fail under `shadow-all` split as 29 JSON content mismatches, 25 HTTP status mismatches,
   14 rows not found by id, 10 assertions, 7 unclassified, 6 empty `Optional`, 1 optimistic lock; 35
   are in shard 8 and 24 in shard 6. One test is red only under `shadow-prod`.
3. The write-attribution detector fails the same 70 tests in both modes. They belong to the live
   list, not to the flip.
4. The fail-closed detector reports without failing a test under a shadow mode, so its backlog is
   in the `failclosed-shadow.txt` signature list of each shard, not in these totals; a green
   `shadow-all` here does not by itself say that no unscoped read remains.
5. Next fixture work, by size: shard 8 (36 % of the red in both modes); the 70 write-attribution
   tests (the same set in both modes); the 20 names above (251 of 524 and 224 of 433), starting with
   `Finding Processing Handling` (46). 67 and 63 failures carry an empty assertion message and need a
   per-test look. Reading the `failclosed-shadow.txt` artifacts is the next measurement.

---

## How to re-measure

The verdict job on the run page carries the per-shard numbers, so the quick answer is to open the
latest Nightly run and read the **Tenant shadow verdict** job. For the breakdown by cause, download
the run's log archive and group the shard logs. The per-job log endpoint
(`actions/jobs/<id>/logs`) answers 404 for these jobs, and `gh run view --json jobs` can answer 502,
so read the archive of the whole run:

```bash
RUN=$(gh run list --workflow=nightly-ci.yml --limit 10 --json databaseId,status,event \
  -q '[.[] | select(.status=="completed" and .event=="schedule")][0].databaseId')
REPO=OpenAEV-Platform/openaev
gh api "repos/$REPO/actions/runs/$RUN" --jq '{run_attempt, head_sha}'
gh api "repos/$REPO/actions/runs/$RUN/logs" > run.zip

mkdir shadow-logs
python3 -I - run.zip shadow-logs <<'EOF'
import re, sys, zipfile
archive, out = sys.argv[1], sys.argv[2]
shard = re.compile(r'API Tests \((?:(\d+)|remaining)-shadow-(all|prod)\)\.txt$')
for entry in zipfile.ZipFile(archive).infolist():
    if '/' in entry.filename:        # nested entries repeat the flat ones
        continue
    m = shard.search(entry.filename)  # excludes (elk9-N-shadow-prod)
    if m:
        text = zipfile.ZipFile(archive).read(entry).decode('utf-8', 'replace')
        text = re.sub(r'\x1b\[[0-9;]*[a-zA-Z]', '', text)
        open(f'{out}/{m.group(2)}-{m.group(1) or "remaining"}.log', 'w').write(text)
EOF
```

The archive holds each shard log twice: a flat entry named `<n>_pipeline _ 🧪 API Tests (<shard>).txt` (the `/` of the
job name becomes `_`) and per-step entries under a folder; the script reads the flat ones; the
filter selects `(N-shadow-all)`, `(N-shadow-prod)` and the two `(remaining-...)` shards, and leaves
out `(elk9-N-shadow-prod)`. Read the checked-out head's active-tables line with `git show
<head_sha>:openaev-api/src/main/resources/application.properties`, and the run attempt from the
first command: the archive holds the logs of the latest attempt.

```bash
cd shadow-logs

# The authoritative totals: one aggregate line per shard. Check each log holds exactly one.
for f in *.log; do echo "$f $(grep -a -cE 'Tests run: .*Skipped: [0-9]+$' "$f")"; done
grep -a -hE 'Tests run: .*Skipped: [0-9]+$' all-*.log

# The dominant shapes. -a: a shard log can hold NUL bytes, and grep then stops printing matches.
cat all-*.log | grep -a -ohE 'violates unique constraint "[A-Za-z0-9_]+"' | sort | uniq -c | sort -rn
cat all-*.log | grep -a -ohE 'Status expected:<(200|400)> but [a-z]+:<[0-9]+>' | sort | uniq -c | sort -rn

# The fail-closed detector: its failure text and its stdout marker.
cat all-*.log | grep -a -c 'Fail-closed: production code read an active tenant table'
cat all-*.log | grep -a -c '^[^ ]* \[FAILCLOSED\]'

# The tests with the most red, from the per-class lines.
cat all-*.log | grep -a -E '<<< (FAILURE|ERROR)! -- in ' |
  sed -E 's/^[^ ]+ //' |
  sed -E 's/^\[ERROR\] Tests run: [0-9]+, Failures: ([0-9]+), Errors: ([0-9]+).* -- in (.*)$/\1 \2 \3/' |
  awk '{n=$1+$2; $1=$2=""; r[$0]+=n} END {for (k in r) print r[k] "\t" k}' | sort -rn | head -20
```

Repeat with `prod-*.log` for `shadow-prod`. Read the aggregate line, never a grep of the build log's
counters: Maven writes ANSI colour codes inside them, which is why the shard logs are stripped
first. The per-class lines sum to the aggregate plus one count per (shard, test) pair printed twice.

The failure families, the de-duplication of the per-class sums and the overlap between the two modes
are not single `grep` counts: they group the per-method failure blocks (`[ERROR] <test> -- Time
elapsed: ... <<< FAILURE!` or `<<< ERROR!` and the lines up to the next block) by shard and test name,
then by the first line of each block. Any small script that does that grouping reproduces them.
