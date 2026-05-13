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

| Domain | File | Applies to |
|---|---|---|
| **Backend** (entities, services, DTOs, API) | [backend.instructions.md](.github/instructions/backend.instructions.md) | `openaev-api/**`, `openaev-model/**` |
| **Frontend** (components, hooks, folders) | [frontend.instructions.md](.github/instructions/frontend.instructions.md) | `openaev-front/**` |
| **Database** (migrations, schema, indexes) | [database.instructions.md](.github/instructions/database.instructions.md) | `**/db/migration/**`, `**/model/**` |
| **Security** (auth, RBAC, tenant isolation) | [security.instructions.md](.github/instructions/security.instructions.md) | All Java & TypeScript files |
| **Performance** (queries, caching, patterns) | [performance.instructions.md](.github/instructions/performance.instructions.md) | All Java files |
| **Multi-Tenancy** (isolation, filters, context) | [multi-tenancy.instructions.md](.github/instructions/multi-tenancy.instructions.md) | Entities, repositories, migrations |
| **Testing** (unit, integration, coverage) | [testing.instructions.md](.github/instructions/testing.instructions.md) | `**/*Test.java`, `**/*.test.tsx` |
| **Code Review** (review checklist) | [code-review.instructions.md](.github/instructions/code-review.instructions.md) | All files |


## Skills (step-by-step procedures)

| Skill | Use when... |
|---|---|
| [add-migration](.github/skills/add-migration/SKILL.md) | Adding a Flyway migration with validation |
| [add-test](.github/skills/add-test/SKILL.md) | Writing tests with coverage verification |
| [add-tenant-isolation-test](.github/skills/add-test/TENANT_ISOLATION.md) | Adding tenant isolation tests to API test classes |
| [create-feature-module](.github/skills/create-feature-module/SKILL.md) | Full feature: entity → API → frontend |
| [review-code](.github/skills/review-code/SKILL.md) | General code review of a PR or module |
| [review-frontend](.github/skills/review-frontend/SKILL.md) | Auditing frontend patterns of a PR or module |
| [review-multi-tenancy](.github/skills/review-multi-tenancy/SKILL.md) | Auditing tenant isolation of a PR or module |
| [review-performance](.github/skills/review-performance/SKILL.md) | Auditing performance of a PR or module |
| [review-security](.github/skills/review-security/SKILL.md) | Auditing security of a PR or module |

## Specialized Agents

| Agent | Role | Reads | Follows |
|---|---|---|---|
| [Code Reviewer](.github/agents/code-reviewer.agent.md) | General-purpose review: architecture, conventions, readability, delegation | `AGENTS.md` → `copilot-instructions.md` → `code-review.instructions.md` | `review-code` skill |
| [Frontend Reviewer](.github/agents/frontend-reviewer.agent.md) | Audit component patterns, forms, MUI, i18n, permissions | `AGENTS.md` → `copilot-instructions.md` → `frontend.instructions.md` | `review-frontend` skill |
| [Multi-Tenancy Reviewer](.github/agents/multi-tenancy-reviewer.agent.md) | Audit tenant isolation, cross-tenant leaks, filter bypasses, migration safety | `AGENTS.md` → `copilot-instructions.md` → `multi-tenancy.instructions.md` | `review-multi-tenancy` skill |
| [Performance Reviewer](.github/agents/performance-reviewer.agent.md) | Audit N+1, lazy loading, query efficiency, pagination | `AGENTS.md` → `copilot-instructions.md` → `performance.instructions.md` | `review-performance` skill |
| [Security Reviewer](.github/agents/security-reviewer.agent.md) | Audit auth, RBAC, data exposure, secrets | `AGENTS.md` → `copilot-instructions.md` → `security.instructions.md` | `review-security` skill |
| [Test Specialist](.github/agents/test-specialist.agent.md) | Write/improve tests, check coverage | `AGENTS.md` → `copilot-instructions.md` → `testing.instructions.md` | `add-test` skill |

## When to Use Which Agent

| Situation | Agent |
|---|---|
| Every PR (first pass) | **Code Reviewer** (delegates to specialists as needed) |
| PR touches `@AccessControl`, `@Filter`, `Capability`, `Permission`, native `@Query` | **Security Reviewer** |
| PR touches entity collections, `@Fetch`, `@Transactional`, new endpoints, `findAll` | **Performance Reviewer** |
| PR touches tenant-scoped entities, migrations with `tenant_id`, `TenantContext` | **Multi-Tenancy Reviewer** |
| PR touches frontend (`.tsx`, `.ts`, forms, components) | **Frontend Reviewer** |
| PR adds a new feature without tests, or coverage is below threshold | **Test Specialist** |
| Critical PR (new entities, migrations, auth changes) | **Code Reviewer** + all relevant specialists |

### Composition Rules

- **Code Reviewer** is the entry point — it runs first and delegates to specialists
- Specialists are **independent** — each focuses on its domain only
- For critical PRs: Code Reviewer explicitly lists which specialists should run
- The Test Specialist does NOT review existing code — it only creates/improves tests
- Human reviewers handle architecture decisions, naming, and business logic