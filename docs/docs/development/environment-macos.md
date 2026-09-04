# Prerequisites macOS

This page lists the tools and dependencies required to build and run OpenAEV from source on macOS (Intel and Apple Silicon).

## Backend

### Homebrew

Install [Homebrew](https://brew.sh/) if it is not already present:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### Java 21

Install OpenJDK 21 and Maven with Homebrew:

```bash
brew install openjdk@21 maven
```

`openjdk@21` is keg-only. Add it to your `PATH` and `JAVA_HOME` (for example in `~/.zshrc`):

```bash
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
```

On Intel Macs, Homebrew uses `/usr/local/opt/openjdk@21` instead of `/opt/homebrew/opt/openjdk@21`.

Verify the installation:

```bash
java -version
mvn -version
```

Both commands must report Java 21. If `java -version` still prints *Unable to locate a Java Runtime*, the `PATH` / `JAVA_HOME` exports above are missing from the current shell.

### Docker

Development services (PostgreSQL, Elasticsearch, RabbitMQ, MinIO) run in containers. Use either Docker Desktop or Colima.

#### Option A — Docker Desktop

1. Download and install [Docker Desktop for Mac](https://docs.docker.com/desktop/setup/install/mac-install/).
2. Start Docker Desktop and wait until the engine is running.
3. Verify the installation:

```bash
docker --version
docker compose version
```

#### Option B — Colima

Colima is a lightweight Docker runtime that works well on Apple Silicon:

```bash
brew install colima docker docker-compose
```

Start a VM with enough memory for Elasticsearch (the compose stack sets a 2 GB heap):

```bash
colima start --cpu 4 --memory 8 --disk 40
```

If Homebrew installed Compose as a Docker CLI plugin, add this to `~/.docker/config.json` so `docker compose` is discovered:

```json
{
  "cliPluginsExtraDirs": [
    "/opt/homebrew/lib/docker/cli-plugins"
  ]
}
```

Verify the installation:

```bash
docker --version
docker compose version
```

!!! tip

    If `docker compose pull` fails with `docker-credential-desktop: executable file not found`, a leftover Docker Desktop `credsStore` entry is still in `~/.docker/config.json`. Remove the `"credsStore": "desktop"` line, or point `DOCKER_HOST` at Colima (`unix://$HOME/.colima/default/docker.sock`) and use a config file that does not reference that helper.

### Git

macOS includes Git through Xcode Command Line Tools:

```bash
xcode-select --install
```

Or install a current Git with Homebrew:

```bash
brew install git
```

## Frontend

### Node.js

Install Node.js 24.11.0 or later with [nvm](https://github.com/nvm-sh/nvm):

```bash
nvm install 24
nvm use 24
nvm alias default 24
```

Verify:

```bash
node -v
```

The frontend `package.json` requires Node.js `>= 24.11.0`.

### Yarn

OpenAEV uses Yarn 4. After installing Node.js, enable Corepack:

```bash
corepack enable
```

Yarn is bundled with the repository and is used automatically when you run `yarn` commands inside `openaev-front/`.

## Development services

Copy the Compose environment file, then start the four required services:

```bash
cd openaev-dev
cp .env.example .env
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

!!! note "Apple Silicon"

    The Elasticsearch and OpenSearch services in `openaev-dev/docker-compose.yml` already set `-XX:UseSVE=0` for M-series compatibility. You do not need to change that flag.

Then copy the Spring Boot development profile and start the backend and frontend as described in [Build from source](build-from-source.md).

!!! tip

    The sample `application-dev.properties.example` enables optional integrations (SAML, Caldera, Tanium, CrowdStrike, IMAP, OVH SMS) that need external credentials. If you do not have those accounts, set the corresponding `*.enable` flags to `false` and set `openaev.listener.smtp.enabled=false` so the backend starts without connection warnings every 10 seconds.

## What's next?

- [Platform development](platform.md) -- Build and run OpenAEV from source
- [Build from source](build-from-source.md) -- Detailed build instructions
- [Prerequisites Ubuntu](environment-ubuntu.md) -- Linux setup instructions
- [Prerequisites Windows](environment-windows.md) -- Windows setup instructions
