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

## Measured on 2026-10-01, from nightly run `36809639051`, head `ab2bdc4d44`

That run checked out `ab2bdc4d4436cab15966b4e0537509fbf8fcce00`, **not** today's `main`. It matters for every
number below: `shadow-prod` reads `openaev.tenant.active-tables` out of `application.properties` at the
checked-out sha, and at `ab2bdc4d44` that line held **35** entries, thirteen fewer than `7faac0b43b`. The
thirteen missing: `collector_types`, `payloads`, `vulnerabilities`, `custom_domains`,
`phishing_email_templates`, `phishing_landing_pages`, `phishing_results`,
`attackpath_execution_collector`, `attackpath_execution_remediation`, `reporting_schedules`, `reportings`,
`reporting_generations`, `datapacks`.

So a `shadow-prod` count of zero for one of those thirteen means "not armed that night", not "fine under the
production list". Any per-constraint row on a table in that list is stale by construction and must be
re-measured; the rows on tables armed in both modes are the ones still worth reading.

Source: the scheduled Nightly run of 2026-10-01 (run `36809639051`), the ten `shadow-all` shards
and the ten Elasticsearch 8 `shadow-prod` shards. The matrix also carries ten `elk9` `shadow-prod`
shards, left out here: they repeat the same suite on the other engine and would double-count.
Method: the job log of each shard, ANSI codes stripped, read at its single Surefire aggregate line.
Each log carries exactly one, checked.

### Headline

| Mode | Shards | Tests | Failures | Errors | Red tests |
|---|---|---|---|---|---|
| `shadow-all` | 10 | 7624 | 1167 | 962 | 28 % |
| `shadow-prod` (elk8) | 10 | 7642 | 1326 | 635 | 26 % |

**The two modes are the same distance from green.** That is the finding. The redness is not
mostly caused by arming the tables that are not active yet: it is already there under the list
`main` ships. Arming everything on top of it adds a few hundred failures, not an order of
magnitude.

This does not say production is broken. The test classpath declares no active-tables line at all,
so the normal pipeline runs with zero active tables and the inspector inert; the suite has simply
never been made to pass under the list `main` ships, with the detectors on.

The 12 September figure of 310 context load failures is gone: 20 occurrences of
`Failed to load ApplicationContext` across the ten `shadow-all` shards, the same 20 under
`shadow-prod`. The context starts.

### Per shard

| Shard | `shadow-all` tests / failures / errors | `shadow-prod` tests / failures / errors |
|---|---|---|
| 1 | 521 / 84 / 45 | 521 / 113 / 9 |
| 2 | 594 / 113 / 116 | 594 / 141 / 77 |
| 3 | 754 / 186 / 127 | 752 / 210 / 55 |
| 4 | 882 / 199 / 56 | 882 / 193 / 56 |
| 5 | 709 / 190 / 60 | 709 / 180 / 46 |
| 6 | 904 / 130 / 197 | 916 / 132 / 174 |
| 7 | 1600 / 59 / 93 | 1600 / 92 / 34 |
| 8 | 797 / 68 / 200 | 805 / 77 / 180 |
| 9 | 503 / 33 / 53 | 503 / 85 / 0 |
| remaining | 360 / 105 / 15 | 360 / 103 / 4 |

### What the failures are

Counted by scanning the same logs for each `<<< FAILURE!` or `<<< ERROR!` block and reading its
first three lines. The proportions below come from that scan; the authoritative totals are the
aggregate lines above.

| Primary cause | `shadow-all` | `shadow-prod` |
|---|---|---|
| The fail-closed detector refused an unscoped read | 763 | 1157 |
| Everything else | 1457 | 873 |

The detector is a third to a half of the red on its own. It is off in the normal pipeline, so none
of that reaches a PR today.

The rest is dominated by a handful of repeated database shapes, counted as occurrences across the
ten `shadow-all` shards:

| Constraint | `shadow-all` | `shadow-prod` | Reading |
|---|---|---|---|
| `domains_domain_name_tenant_key` | 2484 | 2484 | The single largest shape, identical in both modes, so it belongs to the already-live list, not to the flip |
| `collector_types_name_tenant_unique` | 360 | 0 | Pre-#8119 only, and the 0 is "not armed that night". `ensureCollectorTypeExists` looked the row up by name alone and let the v1 listener stamp the tenant, so arming the table hid the other tenant's row and the upsert re-inserted it. Replaced by `findByNameAndTenantId` plus an explicit tenant in `ebdb7b00c9`, merged after this run. Re-measure |
| `injectors_pkey` | 174 | 209 | Injector registration re-inserting |
| `tag_name_tenant_unique` | 70 | 70 | Tag seeding |
| `executors_pkey` | 53 | 53 | Executor registration re-inserting |

Beyond the constraints, the recurring shapes are optimistic locking on batch updates
(`OptimisticLockException`, around 120 occurrences), `Injector not found after initialization:
openaev_implant` (107), and a long tail of status assertions where a request that expects 200 or
400 gets 403, which is the isolation boundary doing its job against a fixture that was never
written for it.

### What this means for retiring the activation list

1. The flip is not gated on fixing a thousand independent tests. It is gated on a short list of
   seeding and registration shapes, each of which accounts for hundreds of failures.
2. `domains` is first, and fixing it is worth doing whatever happens to the flip, because it is
   already failing under the list `main` ships.
3. Until those shapes are fixed, a green `shadow-all` is not reachable, and any claim that the
   nightly is green should be read against this file.

---

## How to re-measure

The verdict job on the run page now carries the per-shard numbers, so the quick answer is to open
the latest Nightly run and read the **Tenant shadow verdict** job. For the breakdown by cause,
download the shard logs and group them:

```bash
RUN=$(gh run list --workflow=nightly-ci.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run view "$RUN" --json jobs \
  -q '.jobs[] | select(.name|test("shadow-all")) | .databaseId' |
while read -r id; do
  gh api --allow-escape-sequences "repos/$GITHUB_REPOSITORY/actions/jobs/$id/logs" |
    sed -E 's/\x1b\[[0-9;]*[a-zA-Z]//g'
done > shadow-all.log

# The authoritative totals: one aggregate line per shard.
grep -hE 'Tests run: .*Skipped: [0-9]+$' shadow-all.log

# The dominant shapes.
grep -ohE 'violates unique constraint "[a-z0-9_]+"' shadow-all.log | sort | uniq -c | sort -rn
```

Read the aggregate line, never a grep of the build log's counters: Maven writes ANSI colour codes
inside them, which is why every command above strips them first.
