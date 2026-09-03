# Platform development

This page explains how to set up a local development environment for the OpenAEV platform, covering both the backend (Java/Spring Boot) and the frontend (React/TypeScript).

## Prerequisites

Before starting, install the required tools for your operating system:

- [Prerequisites Ubuntu](environment-ubuntu.md)
- [Prerequisites macOS](environment-macos.md)
- [Prerequisites Windows](environment-windows.md)

## Architecture overview

OpenAEV is a multi-module Maven project with a React frontend:

| Module | Role |
|---|---|
| `openaev-model` | JPA entities, repositories, domain models |
| `openaev-api` | REST API, services, Flyway migrations, main application |
| `openaev-front` | React SPA (Single Page Application) |
| `openaev-annotation-processor` | Compile-time annotation processing |
| `openaev-maven-plugin` | Database migration tooling |
| `openaev-framework` | Shared abstractions (deprecated -- do not add new code here) |

## Starting development services

Start the backend dependencies with Docker Compose:

```bash
cd openaev-dev
docker compose up -d openaev-dev-pgsql openaev-dev-minio openaev-dev-elasticsearch openaev-dev-rabbitmq
```

## Building the backend

1. Build all modules with the development profile:

```bash
mvn clean install -DskipTests -Pdev
```

2. Check code formatting with Google Java Format:

```bash
mvn spotless:check
```

3. Auto-fix formatting issues:

```bash
mvn spotless:apply
```

## Running the backend

Run the main application class from your IDE or with Maven:

```bash
mvn spring-boot:run -pl openaev-api -Pdev
```

The backend starts on port `8080` by default.

## Building the frontend

1. Navigate to the frontend directory and install dependencies:

```bash
cd openaev-front
yarn install
```

2. Start the development server:

```bash
yarn start
```

The frontend dev server starts on port `3001` and proxies API requests to the backend on port `8080`.

## Running tests

### Backend tests

```bash
mvn test                                          # Run all tests
mvn test -pl openaev-api -Dtest=MyTestClass       # Run a single test class
mvn test -pl openaev-api -Dtest=MyTestClass#myMethod  # Run a single test method
mvn jacoco:check                                  # Verify coverage thresholds
```

!!! note

    Backend tests require Docker services to be running (PostgreSQL, Elasticsearch, RabbitMQ).

### Frontend tests

```bash
cd openaev-front
yarn lint                  # ESLint (must pass with 0 warnings)
yarn check-ts              # TypeScript type checking
yarn test                  # Unit tests (Vitest)
yarn test:e2e              # E2E tests (Playwright, requires running application)
```

## Code style

- **Backend**: Google Java Format, enforced by Spotless. Run `mvn spotless:apply` before committing.
- **Frontend**: ESLint with strict rules. Run `yarn lint` before committing.

## What's next?

- [Build from source](build-from-source.md) -- Detailed build and packaging instructions
- [Database migrations](database-migrations.md) -- Flyway migration guide
- [Injectors](injectors.md) -- Develop custom Injectors
- [Collectors](collectors.md) -- Develop custom Collectors
