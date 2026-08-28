# AGENTS.md

> **Quick reference index.** For full conventions, read [.github/copilot-instructions.md](.github/copilot-instructions.md).

## What is OpenAEV?

OpenAEV — Breach & Attack Simulation platform. Multi-tenant SaaS (**multi-tenancy is actively being developed** — not all entities are tenant-scoped yet).
Java / Spring Boot / React / TypeScript / PostgreSQL. See `pom.xml` and `package.json` for exact versions.

## Modules

| Module | Role | Status |
|---|---|---|
| `openaev-model/` | JPA entities, repositories | Active |
| `openaev-framework/` | Shared abstractions | ⚠️ Deprecated ([details](/.github/copilot-instructions.md#architecture)) |
| `openaev-api/` | REST API, services, migrations | Active |
| `openaev-front/` | React SPA (Redux, CASL, MUI, Zod) | Active |

## Key Commands

```bash
mvn clean install -DskipTests -Pdev   # Build backend
mvn spotless:apply                     # Format Java
mvn test                               # Tests (needs Docker services)
cd openaev-front && yarn build         # Build frontend
yarn lint && yarn check-ts             # Lint + type-check
yarn generate-types-from-api           # Sync API types
```

## Where to find conventions

Do NOT look for conventions here — they live in dedicated instruction files, activated automatically based on the files you touch.

| Domain                                                            | File | Applies to |
|-------------------------------------------------------------------|---|---|
| **Backend** (entities, services, DTOs, API)                       | [backend.instructions.md](.github/instructions/backend.instructions.md) | `openaev-api/**`, `openaev-model/**` |
| **Frontend** (components, hooks, folders)                         | [frontend.instructions.md](.github/instructions/frontend.instructions.md) | `openaev-front/**` |
| **Database** (migrations, schema, indexes)                        | [database.instructions.md](.github/instructions/database.instructions.md) | `**/db/migration/**`, `**/model/**` |
| **Security** (auth, RBAC, tenant isolation)                       | [security.instructions.md](.github/instructions/security.instructions.md) | All Java & TypeScript files |
| **Performance** (queries, caching, patterns)                      | [performance.instructions.md](.github/instructions/performance.instructions.md) | All Java files |
| **ORM** (write correctness, native-query justification, test methodology) | [orm.instructions.md](.github/instructions/orm.instructions.md) | `openaev-api/**`, `openaev-model/**`, `*Test.java` |
| **Multi-Tenancy** (isolation, filters, context)                   | [multi-tenancy.instructions.md](.github/instructions/multi-tenancy.instructions.md) | Entities, repositories, migrations |
| **Testing** (unit, integration, coverage)                         | [testing.instructions.md](.github/instructions/testing.instructions.md) | `**/*Test.java`, `**/*.test.tsx` |
| **Code Review** (review checklist)                                | [code-review.instructions.md](.github/instructions/code-review.instructions.md) | All files |
| **Chaining Engine** (steps, conditions, queues, state, workflows) | [chaining-engine.instructions.md](.github/instructions/chaining-engine.instructions.md) | `**/chaining/**`, `**/QueueChainingJob.java`, `**/WorkflowTimeoutJob.java` |


## Skills (step-by-step procedures)

| Skill | Use when... |
|---|---|
| [add-migration](.github/skills/add-migration/SKILL.md) | Adding a Flyway migration with validation |
| [add-test](.github/skills/add-test/SKILL.md) | Writing tests with coverage verification |
| [add-tenant-isolation-test](.github/skills/add-test/TENANT_ISOLATION.md) | Adding tenant isolation tests to API test classes |
| [create-feature-module](.github/skills/create-feature-module/SKILL.md) | Full feature: entity → API → frontend |
| [review-migration](.github/skills/review-migration/SKILL.md) | Auditing Flyway migration safety and rollout risks |
| [review-code](.github/skills/review-code/SKILL.md) | General code review of a PR or module |
| [review-frontend](.github/skills/review-frontend/SKILL.md) | Auditing frontend patterns of a PR or module |
| [review-multi-tenancy](.github/skills/review-multi-tenancy/SKILL.md) | Auditing tenant isolation of a PR or module |
| [review-performance](.github/skills/review-performance/SKILL.md) | Auditing performance of a PR or module |
| [review-security](.github/skills/review-security/SKILL.md) | Auditing security of a PR or module |
| [review-chaining-engine](.github/skills/review-chaining-engine/SKILL.md) | Reviewing or modifying the Chaining Engine |
| [review-docs](.github/skills/review-docs/SKILL.md) | Detecting missing documentation updates in a PR |

## Specialized Agents

| Agent | Role | Reads | Follows |
|---|---|---|---|
| [Code Reviewer](.github/agents/code-reviewer.agent.md) | General-purpose review: architecture, conventions, readability, delegation | `AGENTS.md` → `copilot-instructions.md` → `code-review.instructions.md` | `review-code` skill |
| [API Reviewer](.github/agents/api-reviewer.agent.md) | Audit API layer: controllers, DTO contracts, Swagger, compatibility | `AGENTS.md` → `copilot-instructions.md` → `api.instructions.md` | n/a |
| [Frontend Reviewer](.github/agents/frontend-reviewer.agent.md) | Audit component patterns, forms, MUI, i18n, permissions | `AGENTS.md` → `copilot-instructions.md` → `frontend.instructions.md` | `review-frontend` skill |
| [Migration Reviewer](.github/agents/migration-reviewer.agent.md) | Audit Flyway migration safety, idempotency, tenant isolation, rollout risk | `AGENTS.md` → `copilot-instructions.md` → `migration.instructions.md` | `review-migration` skill |
| [Multi-Tenancy Reviewer](.github/agents/multi-tenancy-reviewer.agent.md) | Audit tenant isolation, cross-tenant leaks, filter bypasses, migration safety | `AGENTS.md` → `copilot-instructions.md` → `multi-tenancy.instructions.md` | `review-multi-tenancy` skill |
| [Performance Reviewer](.github/agents/performance-reviewer.agent.md) | Audit N+1, lazy loading, query efficiency, pagination | `AGENTS.md` → `copilot-instructions.md` → `performance.instructions.md` | `review-performance` skill |
| [ORM Reviewer](.github/agents/orm-reviewer.agent.md) | Audit ORM doctrine: write correctness (listener chain), native-query justification, composite keys, test methodology | `AGENTS.md` → `copilot-instructions.md` → `orm.instructions.md` | n/a |
| [Security Reviewer](.github/agents/security-reviewer.agent.md) | Audit auth, RBAC, data exposure, secrets | `AGENTS.md` → `copilot-instructions.md` → `security.instructions.md` | `review-security` skill |
| [Test Specialist](.github/agents/test-specialist.agent.md) | Write/improve tests, check coverage | `AGENTS.md` → `copilot-instructions.md` → `testing.instructions.md` | `add-test` skill |
| [Chaining Engine Reviewer](.github/agents/chaining-engine-reviewer.agent.md) | Audit chaining engine: steps, conditions, queues, state, scope, timeout | `AGENTS.md` → `chaining-engine.instructions.md` | `review-chaining-engine` skill |
| [Docs Reviewer](.github/agents/docs-reviewer.agent.md) | Detect functional changes missing documentation updates in `docs/` | `AGENTS.md` → `copilot-instructions.md` | `review-docs` skill |

## When to Use Which Agent

| Situation | Agent |
|---|---|
| Every PR (first pass) | **Code Reviewer** (delegates to specialists as needed) |
| PR touches `io.openaev.api/**`, DTOs, Swagger/OpenAPI annotations | **API Reviewer** |
| PR touches `openaev-api/src/main/java/io/openaev/migration/**` | **Migration Reviewer** |
| PR touches `@AccessControl`, `@Filter`, `Capability`, `Permission`, native `@Query` | **Security Reviewer** |
| PR touches entity collections, `@Fetch`, `@Transactional`, new endpoints, `findAll` | **Performance Reviewer** |
| PR touches tenant-scoped entities, migrations with `tenant_id`, `TenantContext` | **Multi-Tenancy Reviewer** |
| PR touches frontend (`.tsx`, `.ts`, forms, components) | **Frontend Reviewer** |
| PR adds a new feature without tests, or coverage is below threshold | **Test Specialist** |
| PR touches chaining (steps, conditions, workflows, queues, scope, WorkflowState) | **Chaining Engine Reviewer** |
| PR has functional changes but no `docs/` updates | **Docs Reviewer** (auto-triggered on PR open via `/review docs` command) |
| Critical PR (new entities, migrations, auth changes) | **Code Reviewer** + all relevant specialists |

### Composition Rules

- **Code Reviewer** is the entry point — it runs first and delegates to specialists
- Specialists are **independent** — each focuses on its domain only
- For critical PRs: Code Reviewer explicitly lists which specialists should run
- The Test Specialist does NOT review existing code — it only creates/improves tests
- **Scope before code**: for cross-layer tasks (entity + migration + service + controller + frontend), present a scope summary listing every file to create or modify, then wait for maintainer confirmation before generating code
- Human reviewers handle architecture decisions, naming, and business logic

## Shared Severity Rubric

All agents use this rubric as their base. Specialist agents may add domain-specific
severity levels (e.g. `Rollout Safety` for Migration Reviewer) but must not redefine
the base levels below.

| Severity | Criteria | Comment prefix |
|---|---|---|
| 🔴 **CRITICAL / Blocking** | Breaks build, violates architecture, data correctness issue, security vulnerability | `issue (blocking):` — PR must not merge |
| 🟠 **HIGH / Should fix** | Convention violation, missing error handling, code smell | `issue (blocking):` — must fix before merge |
| 🟡 **MEDIUM / Suggestion** | Readability improvement, minor refactor opportunity | `suggestion (non-blocking):` — should fix |
| 🟢 **LOW / Nitpick** | Style preference, naming alternative | `nitpick (non-blocking):` — nice to have |
| 👏 **Praise** | Particularly clean code, good pattern usage, thorough tests | `praise:` |

> **Important**: Include at least one 👏 praise per review. Reviews that only criticize damage team morale.

## Shared Exceptions (What NOT to Flag)

These apply to **all agents**. Do not flag these — they are intentional patterns.

- `skipRBAC = true` with an explanatory comment → intentional, not a bypass
- `@JsonIgnore` on JPA entity relations → correct pattern (never on Output DTOs)
- Platform-level entities (`User`, `Tenant`) without `@Filter` → correct by design
- Dual-scope entities (`Settings`, `User`, `Role`, `Group`) without `@Filter` → correct by design
- `FetchType.EAGER` on `capabilities` / `permissions` collections → intentional for RBAC performance
- Migration using default tenant UUID `2cffad3a-0001-4078-b0e2-ef74274022c3` → standard seed data
- Test files using hardcoded credentials for mock setup → test-only context
- Legacy `io.openaev.rest` controllers passing tenant to services → acceptable (legacy package)

## Agent Maintenance Rule

> **When you modify a `.github/instructions/*.md` file, you must update the corresponding
> `.github/agents/*.agent.md` file in the same PR.**

The mapping is:
| Instruction file | Agent to update |
|---|---|
| `backend.instructions.md` | `code-reviewer.agent.md` |
| `frontend.instructions.md` | `frontend-reviewer.agent.md` |
| `database.instructions.md` | `migration-reviewer.agent.md` |
| `security.instructions.md` | `security-reviewer.agent.md` |
| `performance.instructions.md` | `performance-reviewer.agent.md` |
| `orm.instructions.md` | `orm-reviewer.agent.md` |
| `multi-tenancy.instructions.md` | `multi-tenancy-reviewer.agent.md` |
| `migration.instructions.md` | `migration-reviewer.agent.md` |
| `testing.instructions.md` | `test-specialist.agent.md` |
| `code-review.instructions.md` | `code-reviewer.agent.md` |

If no agent exists yet for a new instruction file → create one following the pattern in `migration-reviewer.agent.md`.


<!-- filigran-conventions:start -->
## Commit, PR & issue conventions

All commits, pull requests and issues in this repository follow the
[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)
specification with a GitHub issue reference:

```
type(scope?)!?: description (#issue)
```

- Types: `feat`, `fix`, `chore`, `docs`, `style`, `refactor`, `perf`, `test`,
  `build`, `ci`, `revert`.
- The description starts with a lowercase letter and has no trailing period;
  preserve acronyms and proper nouns.
- The old `[backend]` / `[frontend]` bracket prefixes are discontinued — use a
  Conventional Commits scope instead.
- Pull request titles **must** end with the related issue reference, e.g.
  `(#1234)`, and every pull request must be linked to an issue.
- Sign your commits.

When generating commit messages, PR titles or issue titles, always follow this
convention. See [`.github/LABELS.md`](.github/LABELS.md) for the full title and
label taxonomy.
<!-- filigran-conventions:end -->


<!-- filigran-model-policy:start -->
## GitHub Copilot model usage

To keep token consumption under control, pick the model that matches the task:

- **Opus 4.6** — reserve for complex work: deep reasoning, large refactors,
  architecture design, tricky debugging. It is significantly more
  token-expensive, so it is not the daily driver.
- **Sonnet / Gemini / GPT** — default for everyday tasks: autocomplete, small
  fixes, quick questions, code explanations.

We have a limited token budget — being mindful of the model you pick makes a
real difference at scale. Think of Opus as a specialist you call in when you
really need it.
<!-- filigran-model-policy:end -->
