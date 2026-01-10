# OpenAEV Development Environment

This folder contains configuration files for setting up a local development environment for OpenAEV.

## Prerequisites

- Docker and Docker Compose
- Java 21+ (for backend development)
- Node.js 20+ and Yarn (for frontend development)
- IntelliJ IDEA (recommended IDE)

## Quick Start

### 1. Set up environment variables

Copy the example environment file and adjust values if needed:

```bash
# Linux/macOS
cp .env.example .env

# Windows (Command Prompt)
copy .env.example .env

# Windows (PowerShell)
Copy-Item .env.example .env
```

The default values should work for local development.

### 2. Start the Docker containers

```bash
docker compose up -d
```

This will start the following services:

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL (dev) | 5432 | Main development database (persistent) |
| PostgreSQL (test) | 5433 | Test database (ephemeral, no volume) |
| MinIO | 10000 (API), 10001 (Console) | Object storage |
| RabbitMQ | 5672 (AMQP), 15672 (Management) | Message queue |
| Caldera | 8888 | Adversary simulation platform |
| Elasticsearch (dev) | 9200, 9300 | Search engine |
| Elasticsearch (test) | 9201, 9301 | Test search engine |
| OpenSearch (dev) | 9202, 9600 | Alternative search engine |
| Kibana (dev) | 5601 | Elasticsearch UI |
| Kibana (test) | 5602 | Test Elasticsearch UI |
| pgAdmin | 5050 | PostgreSQL management UI |

### 3. Access services

- **MinIO Console**: http://localhost:10001 (minioadmin/minioadmin)
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)
- **pgAdmin**: http://localhost:5050 (admin@openaev.io/admin by default, see `.env`)
- **Kibana**: http://localhost:5601
- **Caldera**: http://localhost:8888 (red/ChangeMe or blue/ChangeMe by default, see `caldera.yml`)

## IntelliJ Run Configurations

This folder contains pre-configured IntelliJ run configurations:

- **Backend docker compose**: Starts all Docker containers
- **Backend start**: Starts the Spring Boot backend with the `dev` profile
- **Frontend start**: Starts the frontend development server

To use them, copy the `*.run.xml` files to your `.idea/runConfigurations/` folder or open the project in IntelliJ, which should detect them automatically.

## Configuration Files

| File | Description |
|------|-------------|
| `.env.example` | Example environment variables (copy to `.env`) |
| `docker-compose.yml` | Docker Compose configuration for all services |
| `caldera.yml` | Caldera server configuration |
| `rabbitmq.conf` | RabbitMQ configuration |
| `otlp-config.yaml` | OpenTelemetry Collector configuration (for telemetry) |
| `Project.xml` | IntelliJ code style settings |

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
