---
name: reduce-tx-baseline
description: >-
  Fixes one entry of the frozen background-transaction baseline
  (openaev-api/src/test/resources/archunit_store) and shrinks the store in the same PR.
  Use when asked to fix a @Transactional self-invocation, convert a job away from
  @Transactional or raw TransactionTemplate, or generally to reduce the architecture
  debt recorded by TenantBackgroundTransactionArchTest. Covers the triage of the
  violation type, the sanctioned fix pattern for each, the tests, and the deliberate
  store re-freeze.
---

# Reduce the frozen transaction baseline

The committed store (`openaev-api/src/test/resources/archunit_store/`, see its README) records the
existing violations of the background-transaction rules. Each line is a place where the code
believes it has a transaction or a tenant scope it does not have. This skill treats ONE entry (or
one class's entries) end to end: fix, prove, re-freeze.

## Hard rules

1. **One class per PR.** A store shrink must be reviewable at a glance; batching classes hides
   regressions in the diff noise.
2. **Never hand-edit the store.** The only legitimate change is a re-freeze run (below).
3. **The store diff must contain removals only.** An added line in the same diff means the fix
   introduced a new violation somewhere: stop and look.
4. **TDD when behavior changes.** A self-invocation fix that makes a transaction REAL changes
   rollback and scope semantics: write the test proving the new behavior first (red), then fix.
5. **Re-freeze in the same PR as the fix.** The locked store makes a solved violation FAIL the
   frozen run (`StoreUpdateFailedException`: removing the solved line is a store write, and writes
   are locked). The fix and the smaller store travel together.

## Phase 1 - Triage the entry

Find the entry in the store file (via `stored.rules`) and classify it:

| Type | Store rule | What it means |
|---|---|---|
| A | self-invocation | a method calls a `@Transactional` method of its own class: the proxy is bypassed, no transaction, no scope, silently |
| B | `@Transactional` on a job | the job relies on the annotation; background code must open through the primitive |
| C | raw plumbing in a job | a hand-built `TransactionTemplate` / `PlatformTransactionManager`: a transaction with no tenant scope |

For type A, determine the flavor by reading the CALLER:

- **A1, caller already transactional**: the inner annotation never did anything on this path. The
  behavior does not change; the fix is structural honesty.
- **A2, caller not transactional (HTTP side)**: the callee silently ran without transaction or
  scope. This is a live latent bug: fixing it CHANGES behavior (real atomicity, real scope).
- **A3, background side**: same as A2 but the fix goes through the primitive.

## Phase 2 - Apply the sanctioned fix pattern

| Flavor | Fix |
|---|---|
| A1 | remove the inner `@Transactional` (dead) or restructure so the transactional boundary is the real entry point; document the boundary |
| A2 | extract the transactional method to a collaborator bean and call it through injection (the proxy works again), or make the true entry point `@Transactional` with the `TxCtx` parameter |
| A3 | open through `TenantScopedTransaction.execute(ctx, ...)`; per-tenant work uses `forEachTenant` |
| B | remove `@Transactional` from the job; wrap the work in the primitive with the right scope (`forTenant`, `forEachTenant`, or `allTenants()` for cross-tenant bulk reads and deletes by predicate) |
| C | replace the raw template with the primitive; the scope choice is the same table as B |

Self-injection (`@Autowired` of the class into itself) is NOT a sanctioned fix: the rule flags it
deliberately. Extraction to a collaborator is the honest shape.

Background conversions inherit the primitive's rules: never catch-and-continue inside one
transaction (poisoning), recovery around `executeNew` or a `forEachTenant` iteration. See
`docs/docs/development/tenant-isolation.md`.

## Phase 3 - Prove it

- A2/A3/B/C (behavior changes): red-first test for the new semantics; at minimum a rollback proof
  (fail mid-way, assert nothing committed) and, if the class touches an isolated table, a scope
  proof (models: `TenantScopedTransactionIntegrationTest`, `TenantScopeAllTenantsIntegrationTest`).
- A1 (no behavior change): the class's existing test suite green is enough; say so in the PR.

## Phase 4 - Re-freeze the store

```bash
# one-off: allow the store to shrink, run the frozen rules, re-lock by NOT keeping the flags
mvn -ntp -pl openaev-api test -Dtest='TenantBackgroundTransactionArchTest' \
  -Darchunit.freeze.store.default.allowStoreCreation=true \
  -Darchunit.freeze.store.default.allowStoreUpdate=true

# verify: the diff contains ONLY removals, then run locked to confirm green
git diff --stat openaev-api/src/test/resources/archunit_store/
mvn -ntp -pl openaev-api test -Dtest='TenantBackgroundTransactionArchTest'
```

Commit the smaller store WITH the fix. A reviewer must see, in one diff: the fix, its test, and
the exact baseline lines it retires.

## Definition of Done

- [ ] one class treated, its violation lines gone from the store, no line added anywhere
- [ ] behavior-changing fixes proven red-first (rollback, scope); A1 justified as no-op
- [ ] locked frozen run green after the re-freeze
- [ ] full test suite of the touched module green
- [ ] the PR states the count before/after (the burn-down is the point)
