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

**ALWAYS follow this sequence for first-time setup:**

1. **Start required services** (PostgreSQL, MinIO, Elasticsearch, RabbitMQ):
   ```bash
   cd openaev-dev
   docker-compose up -d openaev-dev-pgsql openaev-dev-minio openaev-dev-elasticsearch openaev-dev-rabbitmq
   ```

2. **Build frontend first** (required by backend):
   ```bash
   cd openaev-front
   yarn install          # Takes ~35s, installs 1207 packages
   yarn build            # Takes ~3-4 minutes, outputs to builder/prod/build/
   ```

3. **Build backend**:
   ```bash
   cd ..
   mvn clean install -DskipTests -Pdev  # Frontend must be built first!
   ```

### Linting & Formatting

**Backend (Java):**
- **ALWAYS run before committing**: `mvn spotless:check`
- **Auto-fix formatting**: `mvn spotless:apply`
- Uses Google Java Format style
- **Known Issue**: There are pre-existing Spotless errors in `openaev-api/src/test/java/io/openaev/api/detection_remediation/DetectionRemediationApiTest.java` and `openaev-api/src/main/java/io/openaev/service/InjectExpectationUtils.java` related to switch statement syntax. These are NOT your responsibility to fix unless directly related to your changes.

**Frontend (TypeScript/React):**
- **Lint check**: `cd openaev-front && yarn lint` (takes ~60s, checks 987 files)
- **TypeScript check**: `cd openaev-front && yarn check-ts`
- **i18n validation**: `cd openaev-front && yarn i18n-checker`

### Testing

**Backend Tests:**
```bash
# Run all tests (requires services running)
mvn test

# Run tests for specific module
cd openaev-api && mvn test
cd openaev-framework && mvn test
```

**Frontend Tests:**
```bash
cd openaev-front
yarn test           # Unit tests with Vitest
yarn test:e2e       # E2E tests with Playwright (requires app running)
```

**Coverage Requirements:**
- Backend: Minimum 50% line coverage, 30% branch coverage (enforced by Jacoco)
- Check with: `mvn jacoco:check` or `mvn verify`

## Continuous Integration

### Drone CI Pipeline (.drone.yml)
The primary CI pipeline runs on every push. **Key steps in order:**

1. **API Tests**:
   - `mvn spotless:check` (formatting)
   - `sleep 60` (waits for services)
   - `mvn clean install -q -DskipTests`
   - `cd openaev-api && mvn test`
   - `mvn jacoco:check` (coverage validation)
   - `cd openaev-framework && mvn test`

2. **Frontend Tests**:
   - `cd openaev-front`
   - `yarn install`
   - `yarn build`
   - `yarn check-ts`
   - `yarn lint`
   - `yarn i18n-checker`
   - `NODE_OPTIONS=--max_old_space_size=8192 yarn test`

3. **E2E Tests**: Starts app, waits for it to be ready, runs Playwright tests

4. **API Type Generation Check**: Verifies types are up-to-date with `yarn generate-types-from-api`

**Services used in CI:**
- PostgreSQL 17-alpine
- MinIO RELEASE.2025-06-13T11-33-47Z
- Elasticsearch 8.18.3
- RabbitMQ 4.1-management

### GitHub Actions Workflows
- **test-feature-branch.yml**: Builds Docker image for feature branches using Alpine Linux
- **codeql.yml**: Security scanning for Java and TypeScript (runs weekly and on master push)
- **pr-title-check-worker.yml**: Validates PR titles follow Conventional Commits format

## Project Structure

### Root Directory Files
- `pom.xml` - Parent Maven POM (Spring Boot 3.3.7, Java 21)
- `.drone.yml` - Primary CI/CD pipeline
- `docker-compose.yml` - Development services (in `openaev-dev/`)
- `Dockerfile` - Production image
- `Dockerfile_ga` - GitHub Actions build image
- `renovate.json` - Dependency management config

### Backend Structure
```
openaev-model/          # Domain models, entities, DTOs
  src/main/java/io/openaev/
    database/           # JPA entities
    model/              # DTOs and domain models

openaev-framework/      # Core framework, utilities, base services
  src/main/java/io/openaev/
    config/             # Configuration classes
    database/           # Database utilities, repositories
    rest/               # Base REST controllers
    service/            # Base services

openaev-api/           # REST API, main application
  src/main/java/io/openaev/
    api/                # REST controllers
    config/             # API-specific configs
    injectors/          # Integration modules (email, SMS, Caldera, etc.)
    service/            # Business logic services
    OpenAevApplication.java  # Main Spring Boot class
  src/main/resources/
    application.properties  # 352 lines of configuration
    db/migration/       # Flyway database migrations
```

### Frontend Structure
```
openaev-front/
  src/
    actions/            # Redux actions
    admin/              # Admin UI components
    components/         # Reusable React components
    constants/          # Constants, enums
    reducers/           # Redux reducers
    static/             # Static assets, locales
    utils/              # Utilities, API types
    index.tsx           # Entry point
  builder/
    dev/                # Dev server scripts
    prod/               # Production build scripts (esbuild)
  packages/
    eslint-plugin-custom-rules/  # Custom ESLint rules
  package.json          # 2.0.10, Node >= 22.11.0
```

### Configuration Files
- **Backend Linting**: `pom.xml` (spotless-maven-plugin with Google Java Format)
- **Frontend Linting**: `eslint.config.js` (not .eslintrc)
- **TypeScript**: `tsconfig.json`
- **Build**: `vite.config.ts`, `builder/prod/prod.js` (custom esbuild)

## Common Issues & Workarounds

### Java Version Mismatch
**Error**: `Fatal error compiling: error: release version 21 not supported`
**Solution**: Ensure Java 21 is installed and active. Check with `java -version`.

### Spotless Formatting Failures
**Error**: `Step 'google-java-format' found problem`
**Solution**: Run `mvn spotless:apply` to auto-fix formatting issues. If errors persist in test files related to switch statement syntax (`case null, default`), these are known pre-existing issues.

### Frontend Build Missing
**Error**: Backend build fails looking for `../openaev-front/builder/prod/build`
**Solution**: **ALWAYS build frontend before backend**. The backend Maven build copies frontend assets during `prepare-package` phase.

### Service Connection Failures
**Error**: Tests fail with connection refused to PostgreSQL/MinIO/Elasticsearch
**Solution**: Ensure Docker Compose services are running. The CI pipeline includes `sleep 60` to wait for services to be ready.

### Memory Issues in Frontend Tests
**Solution**: Use `NODE_OPTIONS=--max_old_space_size=8192` for tests as done in CI.

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

## Important Notes

1. **Trust these instructions**: Only search for information if instructions are incomplete or incorrect.
2. **Pre-existing issues**: Don't fix unrelated linting/build issues unless they block your task.
3. **Frontend must build first**: The backend copies frontend build artifacts.
4. **Services required**: PostgreSQL, MinIO, Elasticsearch/OpenSearch, and RabbitMQ must be running for tests.
5. **Java 21 is mandatory**: The project will not compile with earlier versions.
6. **Node >= 22.11.0**: Required for frontend development.
7. **API types**: After API changes, run `yarn generate-types-from-api` in frontend to update TypeScript types.
8. **Coverage enforcement**: Backend tests must maintain 50% line coverage, 30% branch coverage.
