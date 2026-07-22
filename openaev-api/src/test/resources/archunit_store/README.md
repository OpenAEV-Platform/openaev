# Frozen architecture baseline (do not edit by hand)

This directory is the violation store of the frozen ArchUnit rules in
`io.openaev.architecture.TenantBackgroundTransactionArchTest`. It is not a log: it is the
committed, reviewed inventory of the EXISTING violations of the background-transaction rules,
recorded the day the rules were introduced.

## How to read it

- `stored.rules` maps each rule's full description to the file holding its recorded violations.
- Each other file lists, one line per violation, the code locations that violated the rule when
  the baseline was frozen. An empty file means the rule had zero violations (and must stay that
  way).

## What this debt represents

Each recorded line is a place where the code believes it has a guarantee it does not have:

- a self-invocation of a `@Transactional` method bypasses the Spring proxy, so the method runs
  with NO transaction (no atomicity: a crash mid-way leaves half-written data) and NO tenant
  scope (on isolated tables: empty reads; on legacy-filtered tables: unfiltered reads);
- `@Transactional` on a background job, or raw `TransactionTemplate` plumbing in a job, opens
  transactions that carry no tenant scope; the day the tables they touch become isolated, those
  jobs read zero rows, silently.

The baseline is therefore also the conversion work list: every migration of a class to
`TenantScopedTransaction` (or to an external, proxied call) removes lines from this inventory.

## Rules of the game

1. A test run can neither create nor rewrite this store: it is locked by
   `src/test/resources/archunit.properties` (`allowStoreCreation=false`,
   `allowStoreUpdate=false`).
2. A NEW violation anywhere in production code is not in this store, so it fails the build. That
   is the point.
3. FIXING a recorded violation also fails the locked run: the rule then needs to remove the
   solved line, which is a store write. Refresh the baseline deliberately, in the same PR as the
   fix, with the one-off overrides documented in `archunit.properties`, and commit the smaller
   store. The full procedure (triage, sanctioned fix patterns, tests, re-freeze) is
   `.github/skills/reduce-tx-baseline/SKILL.md`.
4. Never edit these files by hand. A diff in this directory is an architecture event that the
   review must look at: shrinking is progress to verify, anything else is a red flag.

Background: `docs/docs/development/tenant-isolation.md` and
`adr/ADR-002-Multi-tenant-data-isolation-strategy.md`.
