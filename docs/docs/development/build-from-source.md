# Build from source

This page explains how to start the development services, then build and run the OpenAEV backend and frontend.
Run the commands from the root of the repository unless stated otherwise.

!!! tip "Prerequisites"

    Install the tools for your platform first: [Ubuntu](environment-ubuntu.md), [macOS](environment-macos.md)
    or [Windows](environment-windows.md).

## Clone the OpenAEV repository
Obtain a clone of the main OpenAEV repository and navigate to it:
```bash
git clone https://github.com/OpenAEV-Platform/openaev
cd openaev
```

## Backend

### Configuring
Development uses a `dev` Spring profile. Create your local configuration from the example file:

```bash
cp ./openaev-api/src/main/resources/application-dev.properties.example \
   ./openaev-api/src/main/resources/application-dev.properties
```

`application-dev.properties` is gitignored. Generate a UUID for `openaev.admin.token` before the
first start.

!!! tip

    The example enables optional integrations (SAML, Caldera, IMAP, SMS, and others) that need
    external credentials. If you do not have those accounts, set the corresponding `*.enable`
    flags to `false` and set `openaev.listener.smtp.enabled=false`.

!!! note "`host.docker.internal` for RabbitMQ"

    The example sets `openaev.rabbitmq.hostname=host.docker.internal` so Dockerized Injectors can reach
    RabbitMQ. This name does not resolve from the host, so add this entry to your hosts file if it is missing
    (`/etc/hosts` on Linux and macOS, `C:\Windows\System32\drivers\etc\hosts` on Windows, where Docker
    Desktop usually adds it already):

    ```text
    127.0.0.1 host.docker.internal
    ```

    Use `localhost` instead if you do not run Injectors in Docker. Keep the value on its own
    line with no trailing comment — Java properties treat `# ...` on the same line as part of
    the hostname and RabbitMQ will fail to resolve it.

#### Start the development services

Preconfigured containers for the support services (PostgreSQL, Silo, RabbitMQ, Elasticsearch)
live in `./openaev-dev`. Copy the example environment file, then start the four required services:

```bash
cd ./openaev-dev
cp .env.example .env
docker compose up -d openaev-dev-pgsql openaev-dev-minio openaev-dev-elasticsearch openaev-dev-rabbitmq
cd ..
```

| Service | Port | Description |
|---|---|---|
| PostgreSQL | 5432 | Database |
| Silo | 10000, 10001 | S3-compatible object storage |
| Elasticsearch | 9200, 9300 | Search engine |
| RabbitMQ | 5672, 15672 | Message broker (`guest` / `guest`) |

!!! warning

    Ensure these ports are not already in use on your machine before starting the services.

The values in `.env.example` work for local development. See `./openaev-dev/README.md` for optional
services (pgAdmin, Kibana, OpenSearch).

The example `application-dev.properties` already points to these services (PostgreSQL, Silo, RabbitMQ,
Elasticsearch). All settings are listed in the [Configuration documentation](../reference/deployment/configuration.md#dependencies).

### Building and running
Maven is used for package management and building the main server binary.
On a fresh clone, install all modules first, then start the API with the `dev`
Spring profile:
```bash
mvn clean install -DskipTests
mvn spring-boot:run -pl openaev-api -Dspring-boot.run.profiles=dev
```

The backend listens on port `8080`.

The build of `openaev-api` also checks the documentation: see [Documentation check](#documentation-check).

### Documentation check

The build of `openaev-api` builds the documentation in strict mode and fails on any problem the check
reports: a key of `docs/links.json` or a retired path that no longer resolves, a broken link or anchor
between pages, a missing image, a page left out of the navigation, a leftover preview feature block, and so
on. The backend and the frontend open the documentation through the keys of `docs/links.json`, so this
check keeps every link of the application working.

The check needs Python, at least the version in `docs/.python-version`, with its `venv` module: the
prerequisites page of your platform ([Ubuntu](environment-ubuntu.md#documentation-check),
[macOS](environment-macos.md#documentation-check) or [Windows](environment-windows.md#documentation-check))
shows how to install it. The build finds it on the `PATH`. To choose one, set the `OPENAEV_DOCS_PYTHON`
environment variable to its path, or pass `-Dopenaev.docs.python=<path>` to Maven. The
[documentation README](https://github.com/OpenAEV-Platform/openaev/blob/main/docs/README.md#which-python-runs-the-docs-toolchain) explains how
the Python is chosen.

The first run creates a virtual environment in `docs/.venv` and installs the pinned documentation
dependencies in it, which takes about half a minute. When nothing changed in `docs/` since the last
successful check, the check takes less than a second.

When the check cannot run (no usable Python, an install that fails, offline without the dependencies), the
build prints a warning and goes on. Add `-Dopenaev.docs.check.skip=true` to a Maven command to skip it. It
also skips itself in CI, where the blocking Docs Check job runs it.

!!! note "Could not find goal 'docs-check'"

    A build that leaves out the plugin module (`-pl openaev-api` without `-am`, or the offline fast restart
    `mvn -o -pl openaev-model,openaev-framework,openaev-api ...`) uses the `openaev-maven-plugin` installed in
    your local Maven repository (`~/.m2`). If it was installed before the docs check was added, with the same
    version, the build fails with `Could not find goal 'docs-check'`. Install the plugin once (add `-o` when
    offline), then build again, or build with `-Dopenaev.docs.check.skip=true`, which turns the check off before
    Maven looks the goal up:

    ```bash
    mvn -pl openaev-maven-plugin install
    ```

    A full `mvn install -DskipTests` from the repository root installs it too.

## Frontend

Run the commands in this section from `./openaev-front`:
```bash
cd ./openaev-front
```

### Building
Enable Corepack (Yarn 4 is pinned in `package.json`), then install dependencies:
```bash
corepack enable
yarn install
```

If `yarn install` fails during the link step with `ENOENT` while cloning a `node_modules`
path, see [yarnpkg/berry#3214](https://github.com/yarnpkg/berry/issues/3214).

### Running
Execute `yarn start` to start a frontend locally:
```bash
yarn start
```
When the Vite dev server is ready, it prints a local URL (by default
http://localhost:3001/). Start the backend first, otherwise the UI does not load.

### Checking the documentation links
To run the [documentation check](#documentation-check) of the backend build from the frontend:
```bash
yarn check-docs-links
```
Unlike the backend build, it fails when it cannot run, for example when no usable Python is found. It
ignores `-Dopenaev.docs.python`: set `OPENAEV_DOCS_PYTHON` to choose the Python. Extra arguments go to the
check, for example `yarn check-docs-links --force` to rebuild when nothing changed.

!!! tip "IntelliJ IDEA run configurations"

    The repository provides IntelliJ IDEA run configurations in `./openaev-dev`. After you open the
    repository root in IDEA, **Backend start** and **Frontend start** show up in the Run widget.

## What's next?

- [Platform development](platform.md) -- Day-to-day backend and frontend workflow
- [Database migrations](database-migrations.md) -- Create Flyway migrations
- [Prerequisites macOS](environment-macos.md) -- macOS toolchain setup
- [Prerequisites Ubuntu](environment-ubuntu.md) -- Linux toolchain setup
- [Prerequisites Windows](environment-windows.md) -- Windows toolchain setup
