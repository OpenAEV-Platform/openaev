# ADR-023: Large-table data migrations

|  |  |
| --- | --- |
| Status | Proposed |
| Related | #9418 |

## 1. Context

Two production incidents in three weeks filled WAL disks with the same cause: a migration that
rewrites a large table in one unpaced pass.

- 27 Oct: `V6_20261023184049806__Add_expectation_results_table`, one `INSERT ... SELECT` over the
  whole table in one transaction.
- 18 Nov: `V6_20261112060000000__Add_tenant_id_to_execution_traces`, batches in primary key order on
  random UUIDs plus a `VACUUM` every fifty batches: 22x the table size in WAL at 3M rows. Its
  rewrite (#9418), a physical walk by `ctid` page ranges, paced, with no `VACUUM`: 2.6x.

Rule 5 of `migration.instructions.md` ("batch, 1000 rows at a time") reads as satisfied by primary
key order batches, the shape that failed. Rule 2 ("never modify an existing migration") leaves no
way to fix a released migration that is dangerous and has not run everywhere yet.

## 2. Decision

Option B by default, Option C for a table where the walk's startup delay is not acceptable, and
never Option A on a table with random ids, because B fixes the cause in one release, and C costs a
second release, code that tolerates `NULL`, and a second migration that can do B's walk anyway
for an upgrade that skips releases.

A large table is one that grows with the use of the platform (traces, expectations, statuses,
findings, events, logs), as opposed to a catalog table that grows with its configuration (tenants,
roles, groups, tags, settings).

The rules that apply this decision (no `VACUUM`, index `CONCURRENTLY`, constraints `NOT VALID` then
`VALIDATE`, `lock_timeout` with retry, pacing on WAL lag, resumable steps) live in rule 5 of
`migration.instructions.md`; `V6_20261112060000000` is the reference implementation. A shared helper
comes with the second use.

A released migration found dangerous is changed in place only when it is a Java migration (no Flyway
checksum) and the new version reaches the same final state from any state the old one can leave a
platform in. Otherwise a new migration finishes the job.

## 3. Options

### A. Batches in primary key order

**Pros**: simple; the current rule.
**Cons**: on random ids each batch touches pages all over the table, so every checkpoint logs those
pages again in full, and a `VACUUM` between batches reads the whole table again. The 18 Nov incident.

### B. Physical walk in the Flyway migration, paced

**Pros**: one release; each page rewritten once; resumable; measured at 2.6x.
**Cons**: startup waits for the walk, minutes to hours with the table's size.

### C. Background job fills a nullable column, a later migration sets `NOT NULL`

**Pros**: no startup delay for a platform that upgrades one release at a time; the fill can follow
live traffic.
**Cons**: two releases; code must handle `NULL` until the job ends. An upgrade can skip releases,
so both migrations can run back to back before the job ever starts: the second one then fills the
remaining rows itself, in the shape of Option B, and that upgrade waits for it like Option B.

## 4. Consequences

- Authors and the Migration Reviewer agent get a reference implementation and a checkable list.
- Option B is more code than a batched `UPDATE`: pacing, lock retries and resumability.
- `V6_20261014170000000__Add_tenant_id_to_chaining_tables` (seven tables in one transaction) has the
  dangerous shape and is not rewritten here; it is fixed separately once its table sizes in
  production are known.
