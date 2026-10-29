# Installation

All components of OpenAEV are shipped both as [Docker images](https://hub.docker.com/u/openaev) and
manual [installation packages](https://github.com/OpenAEV-Platform/openaev/releases).

!!! note "Production deployment"

    For production deployment, we recommend to deploy all components in containers, including dependencies, using native cloud services or orchestration systems such as [Kubernetes](https://kubernetes.io).

<div class="grid cards" markdown>

-   :simple-docker:{ .lg .middle } __Use Docker__

    ---

    Deploy OpenAEV using Docker and the default `docker-compose.yml` provided
    in the [docker repository](https://github.com/OpenAEV-Platform/docker).

    [:octicons-arrow-right-24:{ .middle } Setup](#using-docker)

-   :material-package-up:{ .lg .middle } __Manual installation__

    ---

    Deploy dependencies and launch the platform manually using the packages
    released in the [GitHub releases](https://github.com/OpenAEV-Platform/openaev/releases).

    [:octicons-arrow-right-24:{ .middle } Explore](#manual-installation)
</div>

!!! tip "Full XTM suite"

    To deploy the full eXtended Threat Management (XTM) suite (OpenCTI, OpenAEV and XTM One) with Docker, use the [XTM Docker repository](https://github.com/FiligranHQ/xtm-docker).

## Using Docker

### Prerequisites

- **Linux**: install [Docker Engine](https://docs.docker.com/engine/install/) with the Compose plugin.
- **Windows and macOS**: install [Docker Desktop](https://www.docker.com/products/docker-desktop).

### Clone the repository

Docker helpers are available in the [Docker GitHub repository](https://github.com/OpenAEV-Platform/docker).

```bash
mkdir -p /path_to_your_app
cd /path_to_your_app
git clone https://github.com/OpenAEV-Platform/docker.git
cd docker
```

### Configure the environment

The `docker-compose.yml` file reads its values from environment variables. Copy [`.env.sample`](https://github.com/OpenAEV-Platform/docker/blob/master/.env.sample) to `.env` and fill in the values for your environment.

### Run OpenAEV

```bash
docker compose up -d
```

!!! success "Installation done"

    You can now navigate to [http://localhost:8080](http://localhost:8080/) and log in with the credentials filled in your configuration.

### Troubleshooting

#### PostgreSQL: password authentication failed

This error occurs when the PostgreSQL container cannot authenticate with the credentials provided in your `.env` file.

**Common causes:**

| Cause | Solution |
|:------|:---------|
| `POSTGRES_USER` set to a reserved name (e.g. `admin`) | Use a different username (e.g. `openaev`) |
| Mismatch between `POSTGRES_USER`/`POSTGRES_PASSWORD` and `SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD` | Ensure both sets of credentials match |

#### Container fails to start

If the OpenAEV container exits immediately after starting:

1. Check the logs: `docker compose logs openaev`
2. Verify all required environment variables are set in your `.env` file
3. Ensure all dependency containers (PostgreSQL, ElasticSearch, RabbitMQ, Silo) are healthy:
   ```bash
   docker compose ps
   ```

## Manual installation

This section explains how to install and run a pre-built OpenAEV server with its dependencies. To build from source, see the [Development section](../../development/build-from-source.md).

### Prepare the installation

#### Installation of dependencies

You have to enable all the mandatory dependencies for the main application.

You may choose to use the dependencies from the provided compose file (see: [Using Docker](#using-docker)).
If you choose to do so, make sure you disable the OpenAEV server container first, and expose the dependencies on
appropriate ports.
You may refer to [the official Docker documentation](https://docs.docker.com/reference/compose-file/) to achieve this.

Otherwise, you are responsible for providing the dependencies yourself by installing and running them.
You need at least a Java Runtime, PostgreSQL (database), ElasticSearch (database), RabbitMQ (queue management), and 
Silo or any other S3-compatible service (for object storage).

!!! note "Supported dependency versions"

    See the [Dependencies section](overview.md#dependencies) for details on the recommended (and supported) versions of the dependencies.

If you choose to install the dependencies manually, please refer to their respective documentation:

* Java: the [Java documentation portal](https://docs.oracle.com/en/java/)
* PostgreSQL: the [PostgreSQL documentation portal](https://www.postgresql.org/docs/)
* ElasticSearch: the [ElasticSearch documentation portal](https://www.elastic.co/docs)
* RabbitMQ: the [RabbitMQ documentation portal](https://www.rabbitmq.com/docs)
* Silo (the community-maintained fork of MinIO): the [Silo documentation](https://silo.pgsty.com/docs/).

#### Download the application files

Download and extract the [latest release file](https://github.com/OpenAEV-Platform/openaev/releases).

```bash
mkdir /path/to/your/app && cd /path/to/your/app
wget https://github.com/OpenAEV-Platform/openaev/releases/download/{RELEASE_VERSION}/openaev-release-{RELEASE_VERSION}.tar.gz
tar xvfz openaev-release-{RELEASE_VERSION}.tar.gz
```

### Install the main platform

#### Configure the application

You may change the `application.properties` file (located at the root of the extracted release archive)
according to your needs; alternatively you may set the equivalent environment variables.

```bash
cd openaev
ls
# application.properties  openaev-api.jar
```

!!! note "Mandatory configuration"

    Note that the configuration keys relevant to the mandatory dependencies listed above must be set in the file or as environment variables.

See the relevant Configuration sections for more details:

- [PostgreSQL](../../reference/deployment/configuration.md#postgresql)
- [ElasticSearch](../../reference/deployment/configuration.md#engine)
- [RabbitMQ](../../reference/deployment/configuration.md#rabbitmq)
- [S3 bucket / Silo](../../reference/deployment/configuration.md#s3-bucket)

#### Start the application

Before you can start the application, ensure your dependencies are up and running, and healthy.

Then start the application itself:

```bash
java -jar openaev-api.jar
```

!!! success "Installation done"

    You can now go to [http://localhost:8080](http://localhost:8080) and log in with the credentials configured in your `application.properties` file.

## Deploy behind a reverse proxy

To serve OpenAEV under a context path, like `https://example.com/openaev`, set the Spring context path and include it in the base URL:

```properties
server.servlet.context-path=/openaev
openaev.base-url=https://example.com/openaev
```

OpenAEV uses WebSockets, so configure your proxy for them. Example with Nginx:

```nginx
location / {
    proxy_cache                 off;
    proxy_buffering             off;
    proxy_http_version          1.1;
    proxy_set_header Upgrade    $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host       $host;
    chunked_transfer_encoding   off;
    proxy_pass                  http://YOUR_UPSTREAM_BACKEND;
}
```

## Community contributions

### Helm charts

<div class="grid cards" markdown>

-   :material-kubernetes:{ .lg .middle } __Kubernetes Helm Charts__

    ---

    OpenAEV Helm Charts for Kubernetes, maintained by the community. See the
    [configuration](https://github.com/devops-ia/helm-openaev/blob/main/charts/openaev/docs/configuration.md)
    and [examples](https://github.com/devops-ia/helm-openaev/blob/main/charts/openaev/docs/examples.md).

    [:material-github:{ .middle } GitHub Repository](https://github.com/devops-ia/helm-openaev/tree/main/charts/openaev)

</div>

## What's next?

- [Authentication](authentication.md) -- Configure local, OpenID or SAML2 login
- [Configuration](../../reference/deployment/configuration.md) -- All platform parameters
- [Upgrade](upgrade.md) -- Upgrade an existing deployment
