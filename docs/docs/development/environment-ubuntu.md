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

The backend build and `yarn check-docs-links` build the documentation to check that every
documentation link of the application, listed in `docs/links.json`, points to an existing page and
section. They need Python with its `venv` module, at least the version in `docs/.python-version`
(3.11): on first use, they create a virtual environment in `docs/.venv` and install the pinned
documentation dependencies in it.

Ubuntu 24.04 ships Python 3.12. Install the `venv` module, which Ubuntu packages apart:

```bash
sudo apt-get install python3 python3-venv
```

The `python3` of Ubuntu 22.04 is 3.10, which is too old. Install a newer Python next to it, for example
from the deadsnakes PPA, with its `venv` module:

```bash
sudo add-apt-repository ppa:deadsnakes/ppa
sudo apt-get install python3.11 python3.11-venv
```

Verify the installation, which must report the version in `docs/.python-version` (3.11) or later:

```bash
python3 --version
```

On Ubuntu 22.04 with deadsnakes, check `python3.11 --version` instead.

The checks use the first Python recent enough among `python3` and `python`, then `python3.N` from the newest
version down. To choose one, set the `PYTHON` environment variable to its path, or pass
`-Dopenaev.docs.python=<path>` to Maven. A Python chosen this way is never replaced by another one:
when it is not found or too old, the check fails and names it.

To build the backend without it, add `-Dopenaev.docs.check.skip=true` to the Maven command.

## What's next?

- [Build from source](build-from-source.md) -- Start the services, then build and run OpenAEV
- [Platform development](platform.md) -- Day-to-day backend and frontend workflow
- [Prerequisites macOS](environment-macos.md) -- macOS setup instructions
- [Prerequisites Windows](environment-windows.md) -- Windows setup instructions
