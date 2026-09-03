# OpenAEV Development Environment

This folder contains configuration files for setting up a local development environment for OpenAEV.

## Prerequisites

- Podman with a Compose provider, or Docker with the Compose plugin
- Java 21+ (for backend development)
- Node.js 24+ and Yarn (for frontend development)
- IntelliJ IDEA (recommended IDE)

On macOS, see the [macOS development guide](../docs/docs/development/environment-macos.md) for Homebrew, Java 21, Node 24, and Docker Desktop or Colima.

## Quick Start

### 1. Set up environment variables

Container image tags live directly in `docker-compose.yml`. Runtime settings such
as credentials are supplied through `.env`, so copy the example and adjust it:

```bash
# Linux/macOS
cp .env.example .env

# Windows (Command Prompt)
copy .env.example .env

# Windows (PowerShell)
Copy-Item .env.example .env
```

The provided values work for local development. If an existing `.env` contains
`COMPOSE_ENV_FILES`, remove that line. Image tags now live in `docker-compose.yml`,
which is also the source used by CI.

### 2. Create the backend dev configuration

Copy the example and fill in your values:

```bash
cp ../openaev-api/src/main/resources/application-dev.properties.example \
   ../openaev-api/src/main/resources/application-dev.properties
```

### 3. Start the containers

#### Minimal start (recommended to get up and running quickly)

Only **4 services** are required to run OpenAEV locally:

```bash
# Podman
podman compose up -d openaev-dev-pgsql openaev-dev-minio openaev-dev-elasticsearch openaev-dev-rabbitmq

# Docker
docker compose up -d openaev-dev-pgsql openaev-dev-minio openaev-dev-elasticsearch openaev-dev-rabbitmq
```

| Service | Port | Why it's required |
|---------|------|-------------------|
| **PostgreSQL (dev)** | 5432 | Primary data store — all entities, users, scenarios |
| **MinIO** | 10000 (API), 10001 (Console) | File/document storage (S3-compatible) |
| **Elasticsearch (dev)** | 9200, 9300 | Full-text search & indexing engine |
| **RabbitMQ** | 5672 (AMQP), 15672 (Management) | Async messaging between backend components |

> **Tip:** If you prefer OpenSearch over Elasticsearch, start `openaev-dev-opensearch` instead and set `engine.engine-selector=opensearch` / `engine.url=http://localhost:9202` in your `application-dev.properties`.

#### Full start (all services)

```bash
# Podman
podman compose up -d

# Docker
docker compose up -d
```

This starts everything, including optional services:

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL (dev) | 5432 | Main development database (persistent) |
| PostgreSQL (test) | 5433 | Test database (ephemeral, no volume) |
| MinIO | 10000 (API), 10001 (Console) | Object storage |
| RabbitMQ | 5672 (AMQP), 15672 (Management) | Message queue |
| Elasticsearch (dev) | 9200, 9300 | Search engine |
| Elasticsearch (test) | 9201, 9301 | Test search engine |
| OpenSearch (dev) | 9202, 9600 | Alternative search engine |
| Kibana (dev) | 5601 | Elasticsearch UI |
| Kibana (test) | 5602 | Test Elasticsearch UI (optional) |
| pgAdmin | 5050 | PostgreSQL management UI (optional) |

### 4. Access services

- **MinIO Console**: http://localhost:10001 (minioadmin/minioadmin)
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)
- **pgAdmin**: http://localhost:5050 (admin@openaev.io/admin by default, see `.env`)
- **Kibana**: http://localhost:5601

## IntelliJ Run Configurations

This folder contains pre-configured IntelliJ run configurations:

- **Backend docker compose**: Starts all containers via Podman
- **Backend start**: Starts the Spring Boot backend with the `dev` profile
- **Frontend start**: Starts the frontend development server

To use them, copy the `*.run.xml` files to your `.idea/runConfigurations/` folder or open the project in IntelliJ, which should detect them automatically.

## Configuration Files

| File | Description |
|------|-------------|
| `.env.example` | Development environment variables (copy to `.env`) |
| `docker-compose.yml` | Container composition file (used via `podman compose`) |
| `rabbitmq.conf` | RabbitMQ configuration |
| `otlp-config.yaml` | OpenTelemetry Collector configuration (for telemetry) |
| `Project.xml` | IntelliJ code style settings |
| `../openaev-api/src/main/resources/application-dev.properties.example` | Example Spring dev profile (copy to `application-dev.properties`) |

## Setup Scripts

| Script | Platform | Description |
|--------|----------|-------------|
| `setup-auto-db.sh` | Linux / macOS / Git Bash | Installs the auto-start database feature |
| `setup-auto-db.ps1` | Windows PowerShell | Same as above |

### Auto-Start Database (`setup-auto-db`)

Copies `DevDatabaseEnvironmentPostProcessor.java` and `spring.factories` from
`test-containers/` into `openaev-api/` so the backend can automatically start a
per-branch PostgreSQL container on launch.

The runtime (Podman or Docker) is **auto-detected** — Podman is preferred when
both are available. You can force a specific runtime via a property.

The copied files are **git-ignored** — they never pollute the API module in version control.

```bash
# Linux / macOS / Git Bash
./setup-auto-db.sh
```

```powershell
# Windows PowerShell
.\setup-auto-db.ps1
```

```properties
# Then add to your application-dev.properties:
openaev.dev.auto-start-database=true

# Optional — force a fixed port instead of per-branch auto-port:
openaev.dev.database-port=5432

# Optional — force a specific container runtime (default: auto-detect):
openaev.dev.container-runtime=podman
```

To uninstall, delete the two copied files:
```bash
rm openaev-api/src/main/java/io/openaev/config/DevDatabaseEnvironmentPostProcessor.java
rm openaev-api/src/main/resources/META-INF/spring.factories
```

## Notes

### Elasticsearch vs OpenSearch

Both Elasticsearch and OpenSearch are included for flexibility. They are configured on different ports to avoid conflicts:

| Engine | HTTP Port | Transport/Metrics Port |
|--------|-----------|----------------------|
| Elasticsearch (dev) | 9200 | 9300 |
| Elasticsearch (test) | 9201 | 9301 |
| OpenSearch (dev) | 9202 | 9600 |

In most cases, you only need to run one search engine at a time. Configure your backend application properties to point to the correct port (9200 for Elasticsearch, 9202 for OpenSearch).

### Apple Silicon Support

The Elasticsearch and OpenSearch configurations include `-XX:UseSVE=0` JVM option for compatibility with Apple Silicon architecture (M1/M2/M3/M4).

### Telemetry (Optional)

To enable OpenTelemetry, uncomment the `openaev-telemetry-otlp` service in `docker-compose.yml`.
