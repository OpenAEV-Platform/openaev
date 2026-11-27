# Prerequisites Ubuntu

This page lists the tools to install on Ubuntu before you [build OpenAEV from source](build-from-source.md).

## Backend

### Java 21, Maven and Git

Install OpenJDK 21, Maven and Git with `apt-get`:

```bash
sudo apt-get update && sudo apt-get install git openjdk-21-jdk maven
```

Verify the installation:

```bash
java -version
mvn -version
```

Both commands must report Java 21. Use Maven 3.9 or later: the `apt` package (3.8.x on Ubuntu 24.04) may be
too old. In that case, install Maven from [maven.apache.org](https://maven.apache.org/download.cgi).

### Docker

Development services (PostgreSQL, Elasticsearch, RabbitMQ, Silo) run in containers. Install Docker Engine and the Compose plugin with the official guide: [Install Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/).

Verify the installation:

```bash
docker compose version
```

## Frontend

### Node.js

The frontend requires Node.js 24.11.0 or later. Install it with [nvm](https://github.com/nvm-sh/nvm/blob/master/README.md#installing-and-updating):

```bash
nvm install 24
nvm alias default 24
```

### Yarn

OpenAEV uses Yarn 4, pinned in `openaev-front/package.json`. Install Corepack, which fetches that version when you run `yarn`:

```bash
npm install -g corepack
corepack enable
```

## Documentation check

The backend build and `yarn check-docs-links` run the [documentation check](build-from-source.md#documentation-check),
which needs Python, at least the version in `docs/.python-version`, with its `venv` module.

On Ubuntu 24.04, `python3` is recent enough, but Ubuntu packages its `venv` module apart. Install it:

```bash
sudo apt-get install python3 python3-venv
```

The `python3` of Ubuntu 22.04 is 3.10, which is too old. Install Python 3.11 next to it, from the Ubuntu
repositories (`jammy-updates`), with its `venv` module. The check finds `python3.11` on its own:

```bash
sudo apt-get install python3.11 python3.11-venv
```

Verify that the Python imports its `venv` module (use `python3.11` instead of `python3` on Ubuntu 22.04):

```bash
python3 --version
python3 -c "import venv, ensurepip"
```

## What's next?

- [Build from source](build-from-source.md) -- Start the services, then build and run OpenAEV
- [Platform development](platform.md) -- Day-to-day backend and frontend workflow
- [Prerequisites macOS](environment-macos.md) -- macOS setup instructions
- [Prerequisites Windows](environment-windows.md) -- Windows setup instructions
