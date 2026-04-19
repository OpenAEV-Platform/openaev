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
| **Testing** (unit, integration, coverage) | [testing.instructions.md](.github/instructions/testing.instructions.md) | `**/*Test.java`, `**/*.test.tsx` |
| **Code Review** (review checklist) | [code-review.instructions.md](.github/instructions/code-review.instructions.md) | All files |


## Skills (step-by-step procedures)

| Skill | Use when... |
|---|---|
| [add-migration](.github/skills/add-migration/SKILL.md) | Adding a Flyway migration with validation |
| [add-test](.github/skills/add-test/SKILL.md) | Writing tests with coverage verification |
| [create-feature-module](.github/skills/create-feature-module/SKILL.md) | Full feature: entity → API → frontend |
| [review-performance](.github/skills/review-performance/SKILL.md) | Auditing performance of a PR or module |
| [review-security](.github/skills/review-security/SKILL.md) | Auditing security of a PR or module |

## Specialized Agents

| Agent | Role | Reads | Follows |
|---|---|---|---|
| [Performance Reviewer](.github/agents/performance-reviewer.agent.md) | Audit N+1, lazy loading, query efficiency | `AGENTS.md` → `copilot-instructions.md` → `performance.instructions.md` | `review-performance` skill |
| [Security Reviewer](.github/agents/security-reviewer.agent.md) | Audit auth, RBAC, tenant isolation, injection | `AGENTS.md` → `copilot-instructions.md` → `security.instructions.md` | `review-security` skill |
| [Test Specialist](.github/agents/test-specialist.agent.md) | Write/improve tests, check coverage | `AGENTS.md` → `copilot-instructions.md` → `testing.instructions.md` | `add-test` skill |
