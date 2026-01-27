# OpenAEV Copilot Instructions

## Repository Overview

**OpenAEV** is an open source platform for planning, scheduling, and conducting cyber adversary simulation campaigns and tests. It helps organizations identify security gaps through simulations, training, and exercises from technical to strategic levels.

### Architecture
- **Backend**: Spring Boot 3.3.7 (Java 21), PostgreSQL, Elasticsearch/OpenSearch, MinIO, RabbitMQ
- **Frontend**: React 19, TypeScript, Vite, Material-UI, Yarn 4.12.0
- **Multi-module Maven project** with 3 modules: `openaev-model`, `openaev-framework`, `openaev-api`
- **Size**: ~1,882 Java files, ~809 TypeScript/React files

## Critical Build Requirements

### Java Version Requirement
**ALWAYS use Java 21**. The project WILL FAIL to build with Java 17 or lower due to:
- Maven compiler plugin configured for Java 21 (`<source>21</source>`, `<target>21</target>`)
- Error message: `release version 21 not supported`

### Node.js Version Requirement
**Use Node.js >= 22.11.0** as specified in `openaev-front/package.json` engines field.

## Build & Development Workflow

### Environment Setup

**ALWAYS follow this sequence:**

1. **Start services**: `cd openaev-dev && docker-compose up -d openaev-dev-pgsql openaev-dev-minio openaev-dev-elasticsearch openaev-dev-rabbitmq`
2. **Build frontend**: `cd openaev-front && yarn install && yarn build` (~4min)
3. **Build backend**: `cd .. && mvn clean install -DskipTests -Pdev`

### Linting & Formatting

**Backend**: `mvn spotless:check` / `mvn spotless:apply` (Google Java Format)
**Frontend**: `yarn lint` (~60s), `yarn check-ts`, `yarn i18n-checker`
**Known Issue**: Pre-existing Spotless errors in `DetectionRemediationApiTest.java` and `InjectExpectationUtils.java` - ignore unless your changes touch these.

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

**Services**: PostgreSQL 17, MinIO RELEASE.2025-06-13T11-33-47Z, Elasticsearch 8.18.3, RabbitMQ 4.1

### GitHub Actions
- **test-feature-branch.yml**: Docker image build (Alpine Linux)
- **codeql.yml**: Security scanning (weekly + master push)
- **pr-title-check-worker.yml**: Conventional Commits validation

## Project Structure

### Root Files
- `pom.xml` - Parent Maven POM (Spring Boot 3.3.7, Java 21)
- `.drone.yml` - Primary CI/CD pipeline
- `docker-compose.yml` - Dev services (in `openaev-dev/`)
- `Dockerfile` / `Dockerfile_ga` - Production / GitHub Actions images

### Backend
```
openaev-model/       # Domain models, entities, DTOs
openaev-framework/   # Core framework, utilities, base services
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
  package.json       # 2.0.10, Node >= 22.11.0
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

## Important Notes

1. **Trust these instructions**: Only search for information if instructions are incomplete or incorrect.
2. **Pre-existing issues**: Don't fix unrelated linting/build issues unless they block your task.
3. **Frontend must build first**: The backend copies frontend build artifacts.
4. **Services required**: PostgreSQL, MinIO, Elasticsearch/OpenSearch, and RabbitMQ must be running for tests.
5. **Java 21 is mandatory**: The project will not compile with earlier versions.
6. **Node >= 22.11.0**: Required for frontend development.
7. **API types**: After API changes, run `yarn generate-types-from-api` in frontend to update TypeScript types.
8. **Coverage enforcement**: Backend tests must maintain 50% line coverage, 30% branch coverage.
