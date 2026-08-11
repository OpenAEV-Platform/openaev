---
applyTo: "openaev-model/src/main/java/**/*.java,openaev-api/src/main/java/**/*.java,openaev-api/src/test/java/**/*Test.java"
description: "ORM doctrine not covered elsewhere: write correctness (listener chain), native-query justification, and query-count test methodology"
---

# ORM Doctrine

This file carries only the ORM rules that the other instruction files do not own. It does not
restate them. For N+1, fetch strategy, pagination, indexing, projections, and returning DTOs
instead of entities at the boundary, see `performance.instructions.md` and `backend.instructions.md`.
For the `WHERE tenant_id` requirement on native queries, see `multi-tenancy.instructions.md`.

## Write correctness (the listener chain)

A native or bulk write (`nativeQuery = true`, `@Modifying @Query`, raw JDBC) skips the Hibernate
session, so `ModelBaseListener` does not run. The search index, the audit log, and the activity
stream are therefore NOT updated by that write.

- Use a native or bulk write only when the target entity is not `@Indexable`, not
  `@AuditDiffTracked`, and not streamed, and no in-session copy of the affected rows is read
  afterward.
- If the entity carries any of those, keep the session-based write, or update the side effect
  explicitly (for example a companion index/audit update). A fast delete that leaves a ghost in
  search is a correctness bug, not an optimization.
- This applies to deletes too: prefer database `ON DELETE CASCADE` plus a session delete of the
  root over a native cascade delete when children are indexed, audited, or streamed.

Detecting the signal: `@Indexable` and `@AuditDiffTracked` are annotations you can grep on the
entity. "Streamed" is behavioral, not an annotation: an entity is streamed when `ModelBaseListener`
publishes a `BaseEvent` for it (gated by `isListened()`). When you cannot tell, treat the entity as
streamed and keep the session write.

## Justify every native query

Every `nativeQuery = true` must carry a one-line comment stating both halves of the rubric:
1. why the ORM cannot express it cleanly (atomic upsert `ON CONFLICT`, `array_agg`, a CTE or
   window function, a set-based operation, or deliberate cross-tenant maintenance), and
2. why no session side effect is lost (the touched rows are not indexed, audited, or streamed).

If the first half fails, the query is unjustified: express it through the ORM (JPQL, Criteria, or a
projection). If the second half fails, it is a write-correctness bug (see above), not a style issue.

## Test methodology

Assert what an ORM change actually controls: the number of entities or SQL statements, read from
Hibernate `Statistics` (`getPrepareStatementCount`, entity load counts). Never assert wall-clock
time as a performance gate; it is machine-dependent and flaky in CI. Timing belongs in a narrative,
the query count belongs in the assertion.

- Exception: a class named `*Benchmark`, or a documented scale-guard that intentionally bounds
  wall-clock time, is allowed to measure time. This rule targets ORM unit/integration tests that
  use elapsed time as a stand-in for a query-count assertion.

## Source and maintenance

Distilled from the cross-team ORM guide maintained by the XTM / backend guild (an external
reference, not a skill in this repository), guide baseline `4829b8d7d`. Re-distill when the guide's
doctrine changes; keep this file to the nucleus above and let the other instruction files own
everything they already cover.
