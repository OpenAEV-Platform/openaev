# OpenAEV Copilot Instructions

## Repository Overview

**OpenAEV** is an open source platform for planning, scheduling, and conducting cyber adversary simulation campaigns and
tests. It helps organizations identify security gaps through simulations, training, and exercises from technical to
strategic levels.

### Architecture

- **Backend**: Spring Boot (Java), PostgreSQL, Elasticsearch/OpenSearch, MinIO, RabbitMQ
- **Frontend**: React, TypeScript, Vite, Material-UI
- **Multi-module Maven project** with 3 modules: `openaev-model`, `openaev-framework`, `openaev-api`
- ⚠️ **`openaev-framework` is deprecated** — it will be removed. **Never add new code to `openaev-framework`**. Place
  new utilities in `openaev-api` or `openaev-model` instead.

> For exact framework/library versions, read `pom.xml` (backend) and `openaev-front/package.json` (frontend).

## Critical Build Requirements

### Java Version Requirement

The project requires the Java version configured in `pom.xml` (`maven-compiler-plugin` source/target).
Building with an older version will fail with: `release version XX not supported`.

### Node.js Version Requirement

The minimum Node.js version is specified in `openaev-front/package.json` (`engines` field).

## Build & Development Workflow

### Environment Setup

**ALWAYS follow this sequence:**

1. **Start services**:
  `cd openaev-dev && if command -v podman >/dev/null 2>&1; then podman compose up -d openaev-dev-pgsql openaev-dev-minio openaev-dev-elasticsearch openaev-dev-rabbitmq; elif command -v docker >/dev/null 2>&1; then docker compose up -d openaev-dev-pgsql openaev-dev-minio openaev-dev-elasticsearch openaev-dev-rabbitmq; else echo "Neither podman nor docker is installed." >&2; exit 1; fi`
2. **Build frontend**: `cd openaev-front && yarn install && yarn build` (~4min)
3. **Build backend**: `cd .. && mvn clean install -DskipTests -Pdev`

### Linting & Formatting

**Backend**: `mvn spotless:check` / `mvn spotless:apply` (Google Java Format)
**Frontend**: `yarn lint` (~60s), `yarn check-ts`, `yarn i18n-checker`
**Known Issue**: Pre-existing Spotless errors in `DetectionRemediationApiTest.java` and `InjectExpectationUtils.java` -
ignore unless your changes touch these.

### Testing

**Backend**: `mvn test` (requires services running), minimum 50% line/30% branch coverage
**Frontend**: `yarn test` (Vitest), `yarn test:e2e` (Playwright, requires app running)
**Coverage check**: `mvn jacoco:check` or `mvn verify`

## Continuous Integration

CI runs on GitHub Actions (see `.github/workflows/`):

1. **API Tests**: `mvn spotless:check`, `mvn clean install -DskipTests`, tests, `mvn jacoco:check`
2. **Frontend Tests**: `yarn install/build/check-ts/lint/i18n-checker/test`
3. **E2E Tests**: Full app test with Playwright
4. **Type Check**: `yarn generate-types-from-api` verification

**Services**: PostgreSQL, MinIO, Elasticsearch, RabbitMQ (see workflow files for exact versions)

### Key Workflows

- **core-ci.yml**: Primary CI pipeline (backend + frontend + e2e)
- **nightly-ci.yml**: Nightly extended test suite
- **test-feature-branch.yml**: Docker image build (Alpine Linux)
- **codeql.yml**: Security scanning (weekly + main push)
- **openaev-validate-pr-title.yml**: Conventional Commits validation

## Project Structure

See the domain-specific instruction files in `.github/instructions/` for detailed package structure, layering rules, and conventions. The "Code Conventions" table below links each domain to its instruction file.

## Common Issues & Workarounds

**Java Version**: Need Java 21 - error `release version 21 not supported` means wrong version
**Spotless Errors**: Run `mvn spotless:apply`; known issues in test files with `case null, default` syntax
**Frontend Missing**: Backend needs frontend built first (copies from `builder/prod/build/`)
**Service Errors**: Ensure Docker services running; CI waits 60s for readiness
**Memory**: Use `NODE_OPTIONS=--max_old_space_size=8192` for frontend tests

## Pre-PR Checklist

Before creating a pull request, validate locally:

1. **Formatting**: `mvn spotless:check` (or via Docker: `docker run --rm -v $(pwd):/app -w /app maven:3.9-eclipse-temurin-21-noble mvn spotless:check`)
2. **PR title**: Must match `type(scope?): description (#issue)` — no `[context]` prefix. The `openaev-pr-checks` GitHub App validates this pattern; titles with extra prefixes (e.g. `[backend]`) will be rejected.
3. **Compile**: `mvn compile -DskipTests` (or via Docker)
4. **Frontend** (if changed): `cd openaev-front && yarn check-ts && yarn lint`

## Code Conventions

Conventions are defined in dedicated instruction files that activate automatically when you work on matching files:

| Domain                               | File                                                                                      |
|--------------------------------------|-------------------------------------------------------------------------------------------|
| Backend (Java/Spring/Hibernate)      | [backend.instructions.md](.github/instructions/backend.instructions.md)                   |
| API Layer (controllers/DTOs/swagger) | [api.instructions.md](.github/instructions/api.instructions.md)                           |
| Frontend (React/TypeScript/MUI)      | [frontend.instructions.md](.github/instructions/frontend.instructions.md)                 |
| Database (schema/migrations/tenancy) | [database.instructions.md](.github/instructions/database.instructions.md)                 |
| Migrations (Flyway/Java-based)       | [migration.instructions.md](.github/instructions/migration.instructions.md)               |
| Multi-Tenancy (isolation/filters)    | [multi-tenancy.instructions.md](.github/instructions/multi-tenancy.instructions.md)       |
| Tests (integration/unit/fixtures)    | [testing.instructions.md](.github/instructions/testing.instructions.md)                   |
| Security (RBAC/@AccessControl)       | [security.instructions.md](.github/instructions/security.instructions.md)                 |
| Performance (N+1/pagination/fetch)   | [performance.instructions.md](.github/instructions/performance.instructions.md)           |
| Code Review                          | [code-review.instructions.md](.github/instructions/code-review.instructions.md)           |

### Available Agents

| Agent                    | Description                                                              |
|--------------------------|--------------------------------------------------------------------------|
| `code-reviewer`          | General-purpose reviewer: architecture, conventions, readability, delegation |
| `security-reviewer`      | Reviews code for RBAC, tenant isolation, data exposure, auth bypasses    |
| `performance-reviewer`   | Reviews code for N+1, fetch strategy, pagination, indexing, memory      |
| `multi-tenancy-reviewer` | Reviews code for tenant isolation, cross-tenant leaks, filter bypasses |
| `frontend-reviewer`      | Reviews frontend for component patterns, forms, permissions, MUI, i18n   |
| `test-specialist`        | Creates and maintains tests following project patterns                   |

## PR & Review Conventions

### Conventional Comments (for code reviews)

Format: `<label>[decorations]: <subject>`

Labels: `praise:`, `nitpick:`, `suggestion:`, `issue:`, `todo:`, `question:`, `thought:`, `chore:`, `note:`, `typo:`

Decorations: `(non-blocking)`, `(blocking)`, `(if-minor)`

Examples:

- `suggestion (non-blocking): prefer functional approach for immutability`
- `todo (blocking): remove debug comments before merging`
- `praise: nice improvement 🤩`

## Important Notes

1. **Follow existing patterns** — before creating anything, search for a similar file and replicate its structure.
2. **Trust these instructions**: Only search for information if instructions are incomplete or incorrect.
3. **Pre-existing issues**: Don't fix unrelated linting/build issues unless they block your task.
4. **Frontend must build first**: The backend copies frontend build artifacts.
5. **Services required**: PostgreSQL, MinIO, Elasticsearch/OpenSearch, and RabbitMQ must be running for tests.
6. **Java 21 is mandatory**: The project will not compile with earlier versions.
7. **Node.js version**: Check `openaev-front/package.json` engines field for the minimum required version.
8. **API types**: After API changes, run `yarn generate-types-from-api` in frontend to update TypeScript types.
9. **Coverage enforcement**: Backend tests must maintain 50% line coverage, 30% branch coverage.
10. **Scope confirmation**: For multi-file or cross-layer changes (entity + migration + service + controller + frontend), state which files you plan to modify and why, then wait for confirmation before writing code.


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
