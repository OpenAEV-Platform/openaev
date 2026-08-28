# Prerequisites Windows

This page lists the tools and dependencies required to build and run OpenAEV from source on Windows.

## Backend

### Java 21

Install OpenJDK 21 or later. Download from [Adoptium](https://adoptium.net/) or use a package manager:

```bash
winget install EclipseAdoptium.Temurin.21.JDK
```

Verify the installation:

```bash
java -version
```

### Maven

Install Apache Maven 3.9 or later. Download from [maven.apache.org](https://maven.apache.org/download.cgi) or use a package manager:

```bash
winget install Apache.Maven
```

Verify the installation:

```bash
mvn -version
```

### Docker Desktop

Docker Desktop is required to run the development services (PostgreSQL, Elasticsearch, RabbitMQ, MinIO).

1. Download and install [Docker Desktop for Windows](https://docs.docker.com/desktop/install/windows-install/).
2. Enable the WSL 2 backend for better performance (recommended).
3. Verify the installation:

```bash
docker --version
docker compose version
```

### Git

Install Git for Windows:

```bash
winget install Git.Git
```

## Frontend

### Node.js

Install Node.js 24 or later. Use [nvm-windows](https://github.com/coreybutler/nvm-windows) to manage versions:

1. Download and install nvm-windows from the [releases page](https://github.com/coreybutler/nvm-windows/releases).
2. Install and activate Node.js 24:

```bash
nvm install 24
nvm use 24
```

### Yarn

OpenAEV uses Yarn 4. After installing Node.js, enable Corepack:

```bash
corepack enable
```

Yarn is bundled with the repository and will be used automatically when running `yarn` commands inside `openaev-front/`.

## Development services

Start the backend dependencies with Docker Compose:

```bash
cd openaev-dev
docker compose up -d openaev-dev-pgsql openaev-dev-minio openaev-dev-elasticsearch openaev-dev-rabbitmq
```

This starts:

| Service | Port | Description |
|---|---|---|
| PostgreSQL 17 | 5432 | Database |
| MinIO | 10000, 10001 | S3-compatible object storage |
| Elasticsearch 8 | 9200 | Analytics engine |
| RabbitMQ 4 | 5672, 15672 | Message broker |

!!! warning

    Ensure these ports are not already in use on your machine before starting the services.

## What's next?

- [Platform development](platform.md) -- Build and run OpenAEV from source
- [Build from source](build-from-source.md) -- Detailed build instructions
- [Prerequisites Ubuntu](environment-ubuntu.md) -- Linux setup instructions
