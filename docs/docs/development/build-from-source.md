!!! tip "Prerequisites"

    Ensure you have followed the steps for installing prerequisites according to your development
    platform of choice:

    * [Linux (Ubuntu used as example)](environment-ubuntu.md)
    * [macOS](environment-macos.md)
    * [Windows](environment-windows.md)

# Build from source

!!! Info "Assumed shell location"

    This documentation assumes that commands listed here are run from the root
    folder of the git repository, unless stated otherwise.

## Clone the OpenAEV repository
Obtain a clone of the main OpenAEV repository and navigate to it:
```shell
git clone https://github.com/OpenAEV-Platform/openaev
cd openaev
```

## Backend

### Configuring
Development uses a `dev` Spring profile. Copy the version-controlled example rather than
`application.properties` (the latter is incomplete for local development):

```shell
cp ./openaev-api/src/main/resources/application-dev.properties.example \
   ./openaev-api/src/main/resources/application-dev.properties
```

`application-dev.properties` is gitignored. Generate a UUID for `openaev.admin.token` before the
first start.

!!! tip

    The example enables optional integrations (SAML, Caldera, IMAP, SMS, and others) that need
    external credentials. If you do not have those accounts, set the corresponding `*.enable`
    flags to `false` and set `openaev.listener.smtp.enabled=false`.

#### Required dependencies

**Start the development dependencies docker stack**

Preconfigured containers for the support services (PostgreSQL, MinIO, RabbitMQ, Elasticsearch)
live in `./openaev-dev`. Copy the example environment file, then start the four required services:

```shell
cd ./openaev-dev
cp .env.example .env
docker compose up -d openaev-dev-pgsql openaev-dev-minio openaev-dev-elasticsearch openaev-dev-rabbitmq
```

The values in `.env.example` work for local development. RabbitMQ uses the image defaults
(`guest` / `guest`) on ports `5672` and `15672`. See `./openaev-dev/README.md` for optional
services (pgAdmin, Kibana, OpenSearch).

**Set up the local development configuration for the OpenAEV server**

Edit `application-dev.properties` so it matches the Compose services. At minimum it must include:

- PostgreSQL
- MinIO
- RabbitMQ
- Engine (Elasticsearch or OpenSearch)

All required settings are listed in the [Configuration documentation](../deployment/configuration.md#dependencies)


### Building and running
Maven is used for package management and building the main server binary.
OpenAEV is a Spring Boot application and thus can be built and started
in one fell swoop with
```shell
mvn spring-boot:run -pl openaev-api -DskipTests -Dspring-boot.run.profiles=dev
```

!!! tip "IntelliJ IDEA run configuration"

    The OpenAEV repository provides predefined IntelliJ IDEA run configurations for
    both the backend. After loading the OpenAEV cloned repository's root
    directory in IDEA, the "Backend start" run configuration will show up in the Run
    widget in the top right corner.


## Frontend
!!! Info "Change the location of your shell"

    In this section, commands need to be run from a subfolder: ./openaev-front

Navigate to `./openaev-front`.
```shell
cd ./openaev-front
```

### Building
Enable Corepack (Yarn 4 is pinned in `package.json`), then install dependencies:
```shell
corepack enable
yarn install
```

If `yarn install` fails during the link step with `ENOENT` on a cloned `node_modules` path, run
it again. The frontend CI job already retries this install once for the same reason.

### Running
Execute `yarn start` to start a frontend locally:
```shell
yarn start
```
The banner should come up soon after:
```
  VITE v8.2.2  ready in 168 ms

  ➜  Local:   http://localhost:3001/
  ➜  Network: use --host to expose
  ➜  press h + enter to show help
```
It is possible to navigate to http://localhost:3001/ by default. Note that the backend needs
to be running otherwise the GUI will not come up in the browser.

!!! tip "IntelliJ IDEA run configuration"

    The OpenAEV repository provides predefined IntelliJ IDEA run configurations for
    both the frontend. After loading the OpenAEV cloned repository's root
    directory in IDEA, the "Frontend start" run configuration will show up in the Run
    widget in the top right corner.

## What's next?

- [Platform development](platform.md) -- Day-to-day backend and frontend workflow
- [Prerequisites macOS](environment-macos.md) -- macOS toolchain setup
- [Prerequisites Ubuntu](environment-ubuntu.md) -- Linux toolchain setup
- [Prerequisites Windows](environment-windows.md) -- Windows toolchain setup
