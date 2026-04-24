# OpenAEV Project Constitution

> Governing principles for all development on the OpenAEV platform.
> This constitution is the source of truth for spec validation, code review, and agent decision-making.

**Version**: 1.0.0 | **Ratified**: 2026-04-24 | **Last Amended**: 2026-04-24

---

## I. Security-First

Every feature, endpoint, and data flow MUST be secure by default.

- **RBAC is mandatory**: every REST endpoint has `@AccessControl` — no exceptions without `skipRBAC = true` + justification
- **Tenant isolation is non-negotiable**: `TenantBase` entities use `@Filter("tenantFilter")`, native queries always include `WHERE tenant_id`
- **No secrets in code**: credentials, API keys, tokens live in environment variables or vaults — never committed
- **Defense in depth**: security controls are enforced end-to-end (API + DB), never only on the frontend
- **Threat modeling**: every new spec gets a security review before implementation begins

## II. Layered Architecture

Clean separation of concerns prevents spaghetti code.

- **Controller → Service → Repository** — never skip layers
- **JPA entities never leave the service layer** — always map to DTOs for API responses
- **Utils are stateless** — static methods only, no injected dependencies
- **`openaev-framework` is deprecated** — all new code goes in `openaev-api` or `openaev-model`
- **One concern per class** — a service handles one domain, not the entire application

## III. Test-First Quality

Tests are not an afterthought — they validate the spec.

- **50% line / 30% branch coverage minimum** — enforced by JaCoCo in CI
- **Integration tests for DB-interacting services** — unit tests only for pure logic
- **`given_X_should_Y` naming** — test names describe the scenario, not the method
- **AAA pattern** — `// Arrange` / `// Act` / `// Assert` in every test
- **Fixtures + Composers** — no inline test data, no duplication across test classes
- **Security tests are mandatory** for auth/RBAC/tenant features

## IV. Performance by Design

Performance is an architecture decision, not a post-launch fix.

- **No N+1 queries** — use `@Fetch(FetchMode.SUBSELECT)` on collections
- **Pagination is mandatory** — all list endpoints return `Page<T>`, never unbounded `List<T>`
- **LAZY by default** — `FetchType.EAGER` only for small, always-needed collections
- **`ReferenceResolver` over `findById()` loops** — 1 COUNT query beats N SELECTs
- **`@Transactional(readOnly = true)` on reads** — disables dirty checking

## V. Spec-Driven Development

Specifications drive implementation, not the other way around.

- **Every feature starts with a spec** — no code without a validated specification
- **Specs are versioned** — stored in `.github/specs/SPEC-NNN-feature-name/`
- **Multi-agent validation** — Product, Staff, and Security agents review every spec before implementation
- **Blockers halt the pipeline** — any agent can raise a blocker that requires human decision
- **Specs evolve** — post-implementation review feeds back into the spec

## VI. Conventional Commits & PRs

Consistent commit messages and PR hygiene.

- **Format**: `[context] type(scope): description (#issue?)`
- **Contexts**: `backend`, `frontend`, `tools`, `agent`, `docs`
- **Types**: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
- **PRs target `release/current`** — never `master` for features
- **One concern per PR** — no bundling unrelated changes
- **Conventional comments in reviews** — `suggestion:`, `issue:`, `todo:`, `praise:`

## VII. Frontend Discipline

The frontend is a first-class citizen with its own standards.

- **No MUI for layout** — use native HTML elements
- **`sx` prop only** — never `makeStyles` / `withStyles`
- **Auto-generated types** — `api-types.d.ts` from API, never manual type definitions
- **Zod + React Hook Form** — for all form validation
- **CASL permissions** — every action/view checks `ability.can()`
- **i18n from day one** — `t()` called early, no raw string keys

## VIII. Simplicity & Anti-Patterns

Avoid complexity that doesn't serve the user.

- **YAGNI** — don't build what isn't needed yet
- **DRY within reason** — extract shared logic, but don't over-abstract
- **No god classes** — if a service exceeds ~500 lines, it needs splitting
- **No shotgun surgery** — a single change shouldn't touch 10+ files across unrelated modules
- **No copy-paste components** — if 3+ components share 80%+ code, extract a generic one
- **No premature optimization** — measure first, optimize second

## Governance

- This constitution supersedes ad-hoc decisions when conflicts arise
- Amendments require documentation and rationale
- All agents (Product, Staff, Security) validate compliance during spec and review phases
- Violations found during review are classified as blockers or suggestions using conventional comments
