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


## Spec-Driven Development (SDD)

OpenAEV uses a spec-driven workflow for feature development. Every feature goes through a multi-agent pipeline before code is written.

### Workflow

```
/spec create "description"  →  Interview → Product → Staff → Security → Spec Ready
/spec plan                  →  Decompose spec into implementation plan + tasks
/spec implement             →  Autonomous development from plan
/spec review                →  Product → Staff → Security review of implementation
/spec test                  →  Tests + security scans + CI validation
```

### Constitution

Project principles are defined in [constitution.md](.github/specs/constitution.md). All agents validate against it.

### Spec Storage

Specs are stored in `.github/specs/SPEC-NNN-feature-name/` with sequential numbering.

### Templates

| Template | Purpose |
|---|---|
| [spec-template.md](.github/templates/spec-template.md) | Feature specification format |
| [plan-template.md](.github/templates/plan-template.md) | Implementation plan format |
| [tasks-template.md](.github/templates/tasks-template.md) | Task breakdown format |

### Security Scanning & CVSS

During `/spec test`, automated security scans run (gitleaks, semgrep, OpenAEV-specific checks). Findings are scored with CVSS v3.1:
- **CVSS < 7.0**: auto-fixed
- **CVSS ≥ 7.0**: consult user before fixing

## Skills (step-by-step procedures)

### SDD Skills

| Skill | Use when... |
|---|---|
| [spec-create](.github/skills/spec-create/SKILL.md) | Creating a new feature spec (interview + multi-agent pipeline) |
| [spec-plan](.github/skills/spec-plan/SKILL.md) | Generating implementation plan from a validated spec |
| [spec-implement](.github/skills/spec-implement/SKILL.md) | Implementing a feature from its plan |
| [spec-review](.github/skills/spec-review/SKILL.md) | Post-implementation review (product + staff + security) |
| [spec-test](.github/skills/spec-test/SKILL.md) | Test + security scan + CI validation pipeline |
| [review-product](.github/skills/review-product/SKILL.md) | Product review: user stories, acceptance criteria, Gherkin |
| [review-staff](.github/skills/review-staff/SKILL.md) | Staff review: architecture, anti-patterns, conventions |

### Implementation Skills

| Skill | Use when... |
|---|---|
| [add-migration](.github/skills/add-migration/SKILL.md) | Adding a Flyway migration with validation |
| [add-test](.github/skills/add-test/SKILL.md) | Writing tests with coverage verification |
| [create-feature-module](.github/skills/create-feature-module/SKILL.md) | Full feature: entity → API → frontend |
| [review-performance](.github/skills/review-performance/SKILL.md) | Auditing performance of a PR or module |
| [review-security](.github/skills/review-security/SKILL.md) | Auditing security of a PR or module |

## Specialized Agents

### SDD Definers (spec creation phase)

| Agent | Role | Reads | Follows |
|---|---|---|---|
| [Product Definer](.github/agents/product-definer.agent.md) | Write Gherkin user stories, acceptance criteria, priorities | `constitution.md` → `copilot-instructions.md` | `review-product` skill |
| [Staff Definer](.github/agents/staff-definer.agent.md) | Map to modules, design schema/API, detect anti-patterns | `constitution.md` → `copilot-instructions.md` → all instruction files | `review-staff` skill |
| [Security Definer](.github/agents/security-definer.agent.md) | Threat model, access control, tenant isolation requirements | `constitution.md` → `security.instructions.md` | `spec-create` skill (§4) |

### SDD Reviewers (post-implementation phase)

| Agent | Role | Reads | Follows |
|---|---|---|---|
| [Product Reviewer](.github/agents/product-reviewer.agent.md) | Verify acceptance criteria coverage, test completeness | spec → changed files | `spec-review` skill (Step 2) |
| [Staff Reviewer](.github/agents/staff-reviewer.agent.md) | Verify code quality, layering, anti-patterns, conventions | spec → changed files → instruction files | `spec-review` skill (Step 3) |
| [Security Reviewer](.github/agents/security-reviewer.agent.md) | Audit RBAC, tenant isolation, CVSS scanning | spec → changed files → `security.instructions.md` | `spec-review` skill (Step 4) + `spec-test` |

### Implementation Agents

| Agent | Role | Reads | Follows |
|---|---|---|---|
| [Performance Reviewer](.github/agents/performance-reviewer.agent.md) | Audit N+1, lazy loading, query efficiency | `AGENTS.md` → `copilot-instructions.md` → `performance.instructions.md` | `review-performance` skill |
| [Test Specialist](.github/agents/test-specialist.agent.md) | Write/improve tests, check coverage | `AGENTS.md` → `copilot-instructions.md` → `testing.instructions.md` | `add-test` skill |
