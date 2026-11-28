---
last_verified: 2026-10-14
---

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

Docker Desktop is required to run the development services (PostgreSQL, Elasticsearch, RabbitMQ, Silo).

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

!!! tip

    The commands in [Build from source](build-from-source.md) use bash syntax. Run them in Git Bash, installed with Git for Windows.

## Frontend

### Node.js

Install Node.js 24.11.0 or later. Use [nvm-windows](https://github.com/coreybutler/nvm-windows) to manage versions:

1. Download and install nvm-windows from the [releases page](https://github.com/coreybutler/nvm-windows/releases).
2. Install and activate Node.js 24:

```bash
nvm install 24
nvm use 24
```

### Yarn

OpenAEV uses Yarn 4, pinned in `openaev-front/package.json`. Install and enable Corepack, which fetches that version when you run `yarn`:

```bash
npm install -g corepack
corepack enable
```

## What's next?

- [Build from source](build-from-source.md) -- Start the services, then build and run OpenAEV
- [Platform development](platform.md) -- Day-to-day backend and frontend workflow
- [Prerequisites Ubuntu](environment-ubuntu.md) -- Linux setup instructions
- [Prerequisites macOS](environment-macos.md) -- macOS setup instructions
