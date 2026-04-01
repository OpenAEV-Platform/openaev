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
   `cd openaev-dev && docker-compose up -d openaev-dev-pgsql openaev-dev-minio openaev-dev-elasticsearch openaev-dev-rabbitmq`
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

### Drone CI Pipeline

Primary CI runs on every push:

1. **API Tests**: `mvn spotless:check`, `mvn clean install -DskipTests`, tests, `mvn jacoco:check`
2. **Frontend Tests**: `yarn install/build/check-ts/lint/i18n-checker/test`
3. **E2E Tests**: Full app test with Playwright
4. **Type Check**: `yarn generate-types-from-api` verification

**Services**: PostgreSQL, MinIO, Elasticsearch, RabbitMQ (see `.drone.yml` for exact versions)

### GitHub Actions

- **test-feature-branch.yml**: Docker image build (Alpine Linux)
- **codeql.yml**: Security scanning (weekly + master push)
- **pr-title-check-worker.yml**: Conventional Commits validation

## Project Structure

### Root Files

- `pom.xml` - Parent Maven POM
- `.drone.yml` - Primary CI/CD pipeline
- `docker-compose.yml` - Dev services (in `openaev-dev/`)
- `Dockerfile` / `Dockerfile_ga` - Production / GitHub Actions images

### Backend

```
openaev-model/       # Domain models, entities, DTOs
openaev-framework/   # ⚠️ DEPRECATED — will be removed (see Architecture section above)
openaev-api/         # REST API, main application
  src/main/java/io/openaev/
    api/             # REST controllers
    injectors/       # Integration modules
    service/         # Business logic
    OpenAevApplication.java
  src/main/resources/
    application.properties  # 352 lines
    db/migration/    # Flyway migrations
```

### Frontend

```
openaev-front/
  src/
    actions/         # Redux actions
    admin/           # Admin UI
    components/      # Reusable components
    utils/           # Utilities, API types
  builder/prod/      # Production build (esbuild)
  package.json
```

### Config Files

- **Backend**: `pom.xml` (spotless-maven-plugin, Google Java Format)
- **Frontend**: `eslint.config.js`, `tsconfig.json`, `vite.config.ts`

## Common Issues & Workarounds

**Java Version**: Need Java 21 - error `release version 21 not supported` means wrong version
**Spotless Errors**: Run `mvn spotless:apply`; known issues in test files with `case null, default` syntax
**Frontend Missing**: Backend needs frontend built first (copies from `builder/prod/build/`)
**Service Errors**: Ensure Docker services running; CI waits 60s for readiness
**Memory**: Use `NODE_OPTIONS=--max_old_space_size=8192` for frontend tests

## Commit Message Format

**ALL commit messages MUST follow Conventional Commits format:**

```
[<context>] <type>(<scope>?): <short description> (#<issue-number>?)
```

**Examples:**

- `[backend] feat(auth): add JWT authentication (#123)`
- `[frontend] fix(ui): resolve button alignment issue`
- `[docs] chore: update README with setup instructions`

**Context values**: `backend`, `frontend`, `tools`, `agent`, `docs`, `[collector-name]`
**Types**: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

## Key Commands Reference

### Backend

```bash
mvn spotless:check              # Check formatting
mvn spotless:apply              # Fix formatting
mvn clean install -DskipTests   # Build without tests
mvn test                        # Run tests
mvn jacoco:check                # Verify coverage
```

### Frontend

```bash
yarn install                    # Install dependencies
yarn build                      # Production build
yarn start                      # Dev server (Vite)
yarn lint                       # ESLint check
yarn check-ts                   # TypeScript check
yarn i18n-checker               # Validate translations
yarn test                       # Run unit tests
yarn test:e2e                   # Run E2E tests
yarn generate-types-from-api    # Generate TypeScript types from API
```

**Checklist:**

- Formatting: `mvn spotless:check` (backend), `yarn lint` (frontend)
- Type safety: `yarn check-ts` (frontend only)
- Tests: Ensure existing tests pass, add tests for new functionality
- Coverage: Maintain 50% line, 30% branch coverage (backend)

## Code Conventions

Conventions are defined in dedicated instruction files that activate automatically when you work on matching files:

| Domain                               | File                                                                            |
|--------------------------------------|---------------------------------------------------------------------------------|
| Backend (Java/Spring/Hibernate)      | [backend.instructions.md](.github/instructions/backend.instructions.md)         |
| Frontend (React/TypeScript/MUI)      | [frontend.instructions.md](.github/instructions/frontend.instructions.md)       |
| Database (schema/migrations/tenancy) | [database.instructions.md](.github/instructions/database.instructions.md)       |
| Tests (integration/unit/fixtures)    | [testing.instructions.md](.github/instructions/testing.instructions.md)         |
| Security (RBAC/@AccessControl)       | [security.instructions.md](.github/instructions/security.instructions.md)       |
| Performance (N+1/pagination/fetch)   | [performance.instructions.md](.github/instructions/performance.instructions.md) |
| Code Review                          | [code-review.instructions.md](.github/instructions/code-review.instructions.md) |

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
