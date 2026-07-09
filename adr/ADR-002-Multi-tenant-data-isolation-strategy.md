# ADR-002: Multi-tenant data isolation strategy

|  |  |
| --- | --- |
| Status | Proposed |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/6393 |
| Epic | https://github.com/OpenAEV-Platform/openaev/issues/4864 |

## 1. Context

OpenAEV hosts several tenants in a single PostgreSQL database. The first isolation mechanism (v1)
relies on a Hibernate `@Filter` on each entity, driven by a thread-local tenant id: every filtered
query gets an `AND tenant_id = :tenantId` predicate when the filter is enabled.

This design has structural limits that grow with the platform:

- it is opt-in per entity and per session: a query path where the filter is not enabled reads
  everything, silently (fail-open);
- the thread-local carries exactly one tenant, while the product needs multi-tenant scopes (a user
  member of several tenants, cross-tenant administration, global maintenance);
- native SQL bypasses Hibernate filters entirely;
- background work (scheduled jobs, consumers, startup tasks) has no request to inherit a tenant
  from, and its transaction handling is spread across `@Transactional` annotations with a known
  silent failure mode (the Spring self-invocation trap).

The v2 strategy described here replaces the per-entity filter with an enforcement point that sees
every statement, fails closed, and covers both the HTTP path and background work. It is rolled out
table by table while v1 keeps protecting the tables not yet migrated.

## 2. Decision drivers

1. **Isolation as an invariant, not a convention**: a forgotten enablement must not become a leak;
   the mechanism must fail closed (missing scope means zero rows, never all rows)
2. **Multi-tenant scopes**: a transaction must be able to carry a list of tenants, not one
3. **Operational simplicity**: one database, one schema, standard backup/upgrade/debug procedures,
   compatible with on-prem deployments
4. **Progressive migration**: tables move from v1 to v2 one reviewed change at a time, with the
   build breaking on regressions rather than on intentions
5. **Maintainability**: the mechanism must be testable with the real database in CI, and its
   bypasses must be detectable at build time

## 3. Considered options

### Option A: keep the v1 Hibernate `@Filter`

Extend the existing per-entity filter to background code and multi-tenant scopes.

**Pros**: no new mechanism, already understood.
**Cons**: structurally fail-open (the filter must be enabled on every session); single-tenant
thread-local at odds with multi-tenant scopes; blind to native SQL; every new entity and every new
code path re-creates the risk. The limits are the reason this ADR exists.

### Option B: one database per tenant

**Pros**: strongest possible isolation; per-tenant backup and restore.
**Cons**: operational cost explodes with tenant count (provisioning, migrations, monitoring,
connection pools per database); cross-tenant features require federation; unrealistic for
on-prem deployments that host many small tenants.

### Option C: one schema per tenant

**Pros**: strong isolation inside one database; per-tenant dump possible.
**Cons**: schema migrations multiply by the tenant count; connection or search-path switching per
request; cross-tenant queries become union gymnastics; ORM tooling and caching assume one schema.

### Option D: PostgreSQL Row-Level Security

Native RLS policies on each table, driven by a session setting.

**Pros**: enforcement inside the database engine itself, below the application; well documented.
**Cons**: policies must be written and maintained per table on a large and moving schema; the
per-row policy cost applies to every plan; debugging query plans under RLS is harder; the
application still has to manage the session setting correctly with pooled connections, so the
application-side discipline problem does not disappear. Writing and reviewing one policy per
table would also double the per-table cost of the migration while it is running. Rejected for
the migration phase, kept in mind as a possible future hardening layer on top of the same scope
channel.

### Option E: application-side SQL rewriting with a transaction-local scope (chosen)

A Hibernate `StatementInspector` rewrites every statement touching an isolated table, adding a
`can_access_tenant(tenant_id)` SQL predicate. The scope (the tenant list) travels in a
transaction-local PostgreSQL setting, `app.current_tenants`, written once per transaction.

**Pros**: fail-closed by construction (no scope, zero rows; unknown SQL shape, refused); sees
native queries that go through Hibernate; supports multi-tenant scopes; single database and
schema; per-table activation makes the migration reviewable and reversible.
**Cons**: the inspector must understand the SQL shapes the platform emits; raw JDBC bypasses it
(must be forbidden separately); the guarantee lives in the application layer, not the database
engine; per-row function cost on large tables must be watched.

## 4. Decision

We chose **Option E** because it is the only option that satisfies driver 1 (fail-closed
invariant) and driver 4 (progressive per-table migration) without the operational multiplication
of options B and C.

Concretely, the strategy is composed of:

- **`TxCtx`**, an explicit scope object (a badge) carried as a method parameter. It is either
  `Missing`, an explicit `Restricted` tenant list, or the `AllTenants` intention.
- **The scope channel**: `set_config('app.current_tenants', <explicit list>, true)`, written once
  at transaction open, transaction-local by construction. Exactly two components may write it: the
  HTTP transaction aspect and the background primitive. A build-time tripwire pins that inventory.
- **The statement inspector**: rewrites reads and writes on isolated tables with
  `can_access_tenant`; refuses any statement shape it cannot rewrite (fail-closed on both axes).
- **Write attribution**: a new row belongs to exactly one tenant, resolved by
  `TenantWriteScopeResolver`; an ambiguous multi-tenant write is refused.
- **The background primitive** (`TenantScopedTransaction`): background code never uses
  `@Transactional` (silent self-invocation trap). It opens transactions through an explicit object
  that sets the scope at open, refuses scope-less or ambiguous usage, resolves the `AllTenants`
  intention into an explicit tenant list (no wildcard ever reaches the database), and provides a
  per-tenant loop (`forEachTenant`) that isolates failures per tenant.
- **Per-table activation**: the `openaev.tenant.active-tables` property lists the isolated tables.
  Migrating a table is one reviewed change: scope every code path, remove the v1 `@Filter`, append
  the table to the list, extend the guards. A step-by-step runbook is maintained in-repo.
- **Build-time guards**: architecture tests pin the reviewed access surface of each active table,
  the `TxCtx` signature of registered endpoints, the raw-JDBC ban, and the background transaction
  rules, with pre-existing violations frozen in a committed, locked baseline so only new
  violations break the build.

## 5. Consequences

### Positive

- Isolation failures are designed to surface as silent absences rather than leaks: a missing
  scope reads zero rows, an unsupported SQL shape is refused.
- Multi-tenant scopes are first-class, which unlocks cross-tenant features without weakening
  isolation.
- The migration is incremental and auditable: one table, one reviewed change, guarded by tests
  that run against the real database.
- Background work gets the same enforcement as HTTP, through one explicit door.

### Negative / trade-offs

- Fail-closed trades availability for safety: a forgotten scope or an un-rewritable native query
  breaks a feature loudly (or renders it empty) instead of leaking. Teams must learn the
  troubleshooting reflexes, and every native query on an isolated table needs test coverage.
- The inspector is a piece of security-critical parsing code that must follow the SQL the
  platform emits; unsupported shapes surface as refusals.
- The guarantee is enforced in the application layer. Raw JDBC and direct database access sit
  outside it, so they are forbidden by build-time rules rather than by the engine.
- Activating a table costs real work (path inventory, scope wiring, tests, guard extensions), and
  the per-row cost of `can_access_tenant` on large, hot tables still needs measurement at scale.
- The build-time guards are minimum checks, not proofs: they pin the reviewed surface but cannot
  verify at runtime that every access carries a scope. A stronger runtime option (raising an error
  instead of returning empty when the scope is absent) is a candidate follow-up.

### Neutral

- v1 and v2 coexist during the migration: tables not yet activated keep their v1 `@Filter`, and
  the thread-local tenant keeps attributing writes on v1 tables.
- The search engine indexes are isolated by a separate mechanism, out of scope here.
- The scope channel design leaves room to move enforcement into the database engine later (RLS on
  top of the same `app.current_tenants` setting) without changing callers.
