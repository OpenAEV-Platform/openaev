---
applyTo: "openaev-model/src/main/java/**/*.java,openaev-api/src/main/java/**/*.java,openaev-api/src/test/java/**/*Test.java"
description: "ORM doctrine not covered elsewhere: write correctness (listener chain), native-query justification, composite keys, inheritance proxies, and query-count test methodology"
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

## Composite keys

When an entity's real primary key is composite (for example `(id, tenant_id)`), its `@Id` /
`@IdClass` / `@EmbeddedId` must cover every key column. A single-column `@Id` on a composite-key
table is a correctness bug: an update or lookup keyed on the partial id can match another tenant's
rows (it surfaces as `TooManyRowsAffectedException` on writes), so the mapping's identity is not
tenant-safe.

## Inheritance and proxies

A lazy association to a `@Inheritance(SINGLE_TABLE)` base type (for example `Asset`, `Payload`)
loads as a base-type proxy, so `instanceof Subclass` is false and subclass state is hidden until the
proxy is initialized. `Hibernate.unproxy(...)` unwraps it, but frequent unproxy calls signal a
polymorphic association being worked around, not a fix.

- Do not branch on `instanceof` against a lazy polymorphic association; it sees the proxy, not the
  concrete class.
- Where a path needs the concrete subclass, fetch it explicitly (`JOIN FETCH` / `@EntityGraph`)
  instead of scattering `Hibernate.unproxy(...)`.

## Entity identity (equals / hashCode)

An entity's `equals`/`hashCode` is based on its id only, never on its fields.

- A mapped `Set` or `Map` is, once loaded, a `PersistentSet` / `PersistentMap` whose `hashCode` and
  `equals` initialize the whole collection. A field-based identity (Lombok `@Data` /
  `@EqualsAndHashCode`) therefore loads every element each time the owner is put in a `HashSet`,
  used as a `Map` key or compared: #8443, an endpoint targeted by many simulations loaded all its
  injects on every hash, up to an `OutOfMemoryError`. A lazy to-one in the hash costs one query
  per hash for the same reason (the proxy initializes).
- A mapped `List` (`PersistentBag`) hashes by identity and never loads, but then two instances of
  the same row hash differently.
- Use hand-written methods (preferred; they also stop `@Data` from generating its own): `equals` =
  same type and `id != null && id.equals(other.id)`, `hashCode` = `Objects.hash(id)` (see `Inject`,
  `Endpoint`). Lombok `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with
  `@EqualsAndHashCode.Include` on the id only is also compliant (see `Step`, `Workflow`). Avoid
  `@EqualsAndHashCode.Exclude` on a collection: the next field added is included again.
- Before switching a `List` to a `Set` (for example to clear `TenantFilteredBagCollectionsTest`),
  check the owner's identity. `add()` on a lazy `Set` also initializes it, where a bag only queued
  the element.

Enforced by `EntityIdentityIgnoresCollectionsTest`: it fails if any entity's `equals`/`hashCode`
reads a mapped `Set` or `Map`.

## Test methodology

Assert what an ORM change actually controls: the number of entities or SQL statements, read from
Hibernate `Statistics` (`getPrepareStatementCount`, entity load counts). Never assert wall-clock
time as a performance gate; it is machine-dependent and flaky in CI. Timing belongs in a narrative,
the query count belongs in the assertion.

- Exception: a class named `*Benchmark`, or a documented scale-guard that intentionally bounds
  wall-clock time, is allowed to measure time. This rule targets ORM unit/integration tests that
  use elapsed time as a stand-in for a query-count assertion.

## Maintenance

This file is a hand-maintained digest of the cross-team ORM guide. Re-sync it when the guide's
doctrine changes. Owner: XTM / backend guild.
