# OpenAEV Development Environment

This folder contains configuration files for setting up a local development environment for OpenAEV.

## Prerequisites

- Podman with a Compose provider, or Docker with the Compose plugin
- Java 21+ (for backend development)
- Node.js >= 24.11.0 and Yarn (for frontend development)
- IntelliJ IDEA (recommended IDE)

On macOS, see the [macOS development guide](../docs/docs/development/environment-macos.md) for Homebrew, Java 21, Node.js >= 24.11.0, and Docker Desktop or Colima.

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
| **Silo** | 10000 (API), 10001 (Console) | File/document storage (S3-compatible, MinIO fork) |
| **Elasticsearch (dev)** | 9200, 9300 | Full-text search & indexing engine |
| **RabbitMQ** | 5672 (AMQP), 15672 (Management) | Async messaging between backend components |

> **Tip:** If you prefer OpenSearch over Elasticsearch, start `openaev-dev-opensearch` instead and set `engine.url=http://localhost:9202` in your `application-dev.properties`: the engine is detected automatically.

#### Full start (all services)

```bash
# Podman
podman compose up -d

# Docker
docker compose up -d
```

This starts everything, including optional services, except the alternative search engines (OpenSearch and
Elasticsearch 8): OpenAEV uses one engine at a time, so they sit behind a profile and only start when asked for.

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL (dev) | 5432 | Main development database (persistent) |
| PostgreSQL (test) | 5433 | Test database (ephemeral, no volume) |
| Silo | 10000 (API), 10001 (Console) | Object storage (S3-compatible) |
| RabbitMQ | 5672 (AMQP), 15672 (Management) | Message queue |
| Elasticsearch (dev) | 9200, 9300 | Search engine |
| Elasticsearch (test) | 9201, 9301 | Test search engine |
| Elasticsearch 8 | 9203, 9303 | Oldest supported Elasticsearch major (profile `es8`, ephemeral) |
| OpenSearch (dev) | 9202, 9600 | Alternative search engine (profile `opensearch`) |
| Kibana (dev) | 5601 | Elasticsearch UI |
| Kibana (test) | 5602 | Test Elasticsearch UI (optional) |
| pgAdmin | 5050 | PostgreSQL management UI (optional) |
| Keycloak | 8180 | Local SSO provider (profile `keycloak`, optional) |

### 4. Access services

- **Silo Console**: http://localhost:10001 (minioadmin/minioadmin)
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
| `../openaev-api/src/main/resources/application-keycloak.properties.example` | Local Keycloak SSO settings (copy to `application-keycloak.properties`, profile `keycloak`) |
| `keycloak/openaev-dev-realm.json` | Keycloak realm with the SSO test accounts |

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
| Elasticsearch 8 | 9203 | 9303 |
| OpenSearch (dev) | 9202 | 9600 |

OpenSearch and Elasticsearch 8 sit behind a compose profile, so a plain `up -d` does not start them. Start one by
naming it, or with its profile:

```bash
docker compose up -d openaev-dev-opensearch      # or: docker compose --profile opensearch up -d
docker compose up -d openaev-dev-elasticsearch8  # or: docker compose --profile es8 up -d
```

With Podman, `--profile` needs a recent `podman-compose`; naming the service works everywhere.

In most cases, you only need to run one search engine at a time. Point `engine.url` at the one you run (9200 for Elasticsearch, 9203 for Elasticsearch 8, 9202 for OpenSearch): the engine is detected automatically.

### Local SSO with Keycloak (Optional)

`openaev-dev-keycloak` is an OpenID Connect provider with ready-made test accounts, to test SSO login and group mapping.

1. Start Keycloak:

   ```bash
   podman compose up -d openaev-dev-keycloak
   ```

2. Copy the Keycloak settings next to your `application-dev.properties`:

   ```bash
   cp ../openaev-api/src/main/resources/application-keycloak.properties.example \
      ../openaev-api/src/main/resources/application-keycloak.properties
   ```

3. Start the backend with the `dev,keycloak` profiles instead of `dev`:

   ```bash
   mvn -pl openaev-api spring-boot:run -Dspring-boot.run.profiles=dev,keycloak
   ```

   In IntelliJ, set **Active profiles** to `dev,keycloak` in the run configuration.

4. Open http://localhost:3001, click **Login with Keycloak** and log in with one of the accounts below.

Without the `keycloak` profile, the backend starts as usual and Keycloak is not used.

| Account (password = account name) | Groups in the token | OpenAEV group |
|-----------------------------------|---------------------|---------------|
| `platform-observer` | `platform-observers` | SSO Platform Observers (platform) |
| `platform-manager` | `platform-managers` | SSO Platform Managers (platform) |
| `platform-admin` | `platform-admins` | SSO Platform Admins (platform) |
| `tenant-observer` | `tenant-observers` | SSO Tenant Observers (default tenant) |
| `tenant-manager` | `tenant-managers` | SSO Tenant Managers (default tenant) |
| `tenant-admin` | `tenant-admins` | SSO Tenant Admins (default tenant) |
| `super-admin` | none, `admin` role | OpenAEV administrator (`roles_admin`) |

- Groups created at login have no role: give each one its roles once in OpenAEV.
- Keycloak admin console: http://localhost:8180 (admin/admin). The realm (`openaev-dev`) is loaded from
  `keycloak/openaev-dev-realm.json` on first start; remove the container to reload it.
- Use a private window to switch accounts: Keycloak keeps you logged in.
- Windows with Podman: if the backend fails with `Unable to resolve Configuration with the provided Issuer`, Keycloak
  only answers on IPv6 `localhost`. Start the backend with `-Djava.net.preferIPv6Addresses=true`.

### Apple Silicon Support

The Elasticsearch and OpenSearch configurations include `-XX:UseSVE=0` JVM option for compatibility with Apple Silicon architecture (M1/M2/M3/M4).

### Telemetry (Optional)

To enable OpenTelemetry, uncomment the `openaev-telemetry-otlp` service in `docker-compose.yml`.
