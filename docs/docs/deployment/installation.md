# Installation

All components of OpenAEV are shipped both as [Docker images](https://hub.docker.com/u/openaev) and
manual [installation packages](https://github.com/OpenAEV-Platform/openaev/releases).

!!! note "Production deployment"

    For production deployment, we recommend to deploy all components in containers, including dependencies, using native cloud services or orchestration systems such as [Kubernetes](https://kubernetes.io).

<div class="grid cards" markdown>

-   :simple-docker:{ .lg .middle } __Use Docker__

    ---

    Deploy OpenAEV using Docker and the default `docker-compose.yml` provided
    in the [docker](https://github.com/OpenAEV-Platform/docker).

    [:octicons-arrow-right-24:{ .middle } Setup](#using-docker)

-   :material-package-up:{ .lg .middle } __Manual installation__

    ---

    Deploy dependencies and launch the platform manually using the packages
    released in the [GitHub releases](https://github.com/OpenAEV-Platform/openaev/releases).

    [:octicons-arrow-right-24:{ .middle } Explore](#manual-installation)
</div>

## Using Docker

### Introduction

OpenAEV can be deployed using the *docker compose* command.

### Pre-requisites

**:material-linux:{ .middle } Linux**

```bash
sudo apt install docker-compose
```

**:material-microsoft-windows:{ .middle } Windows and MacOS**

Just download the appropriate [Docker for Desktop](https://www.docker.com/products/docker-desktop) version for your
operating system.

### Clone the repository

Docker helpers are available in the [Docker GitHub repository](https://github.com/OpenAEV-Platform/docker).

```bash
mkdir -p /path_to_your_app
cd /path_to_your_app
git clone https://github.com/OpenAEV-Platform/docker.git
cd docker
```

### Configure the environment

Before running the `docker compose` command, the `docker-compose.yml` file should be configured. By default, the
`docker-compose.yml` file is using environment variables available in the `.env.sample` file, available [here](https://github.com/OpenAEV-Platform/docker/blob/master/.env.sample).

You can either rename the file `.env.sample` in `.env` and put the expected values or just fill directly the
`docker-compose.yml` with the values corresponding to your environment.

### Run OpenAEV

After changing your `.env` file run `docker compose` in detached (-d) mode:

```bash
sudo systemctl start docker.service
# Run docker compose in detached
docker compose up -d
```

!!! success "Installation done"

    You can now navigate to [http://localhost:8080](http://localhost:8080/) and log in with the credentials filled in your configuration.

## Manual installation

This section provides instructions to install and run a pre-built OpenAEV server with its dependencies. Note that this
does not cover building from source,
which you will find in the [Development section](../development/build_from_source.md) instead.

### Prepare the installation

#### Installation of dependencies

You have to enable all the mandatory dependencies for the main application.

You may choose to use the dependencies from the provided compose file (see: [Using Docker](#using-docker)).
If you choose to do so, make sure you disable the OpenAEV server container first, and expose the dependencies on
appropriate ports.
You may refer to [the official Docker documentation](https://docs.docker.com/reference/compose-file/) to achieve this.

Otherwise, you are responsible for providing the dependencies yourself by installing and running them.
You need at least a Java Runtime, PostgreSQL (database), ElasticSearch (database), RabbitMQ (queue management), and 
MinIO (for object storage).

!!! note "Supported dependency versions"

    See the [Dependencies section](platform/overview.md#dependencies) for details on the recommended (and supported) versions of the dependencies.

If you choose to install the dependencies manually, please refer to their respective documentation:

* Java: the [Java documentation portal](https://docs.oracle.com/en/java/)
* PostgreSQL: the [PostgreSQL documentation portal](https://www.postgresql.org/docs/)
* ElasticSearch: the [ElasticSearch documentation portal](https://www.elastic.co/docs)
* RabbitMQ: the [RabbitMQ documentation portal](https://www.rabbitmq.com/docs)
* MinIO: the [MinIO website](https://min.io/docs).

#### Download the application files

First, you have to [download and extract the latest release file](https://github.com/OpenAEV-Platform/openaev/releases).

```bash
mkdir /path/to/your/app && cd /path/to/your/app
wget <https://github.com/OpenAEV-Platform/openaev/releases/download/{RELEASE_VERSION}/openaev-release-{RELEASE_VERSION}.tar.gz>
tar xvfz openaev-release-{RELEASE_VERSION}.tar.gz
```

### Install the main platform

#### Configure the application

You may change the `application.properties` file (located at the root of the extracted release archive)
according to your needs; alternatively you may set the equivalent environment variables.

```shell
$ cd openaev
$ ls
application.properties  openaev-api.jar
```

!!! note "Mandatory configuration"

    Note that the configuration keys relevant to the mandatory dependencies listed above must be set in the file or as environment variables.

See the relevant Configuration sections for more details:

- [PostgreSQL](configuration.md#postgresql)
- [ElasticSearch](configuration.md#engine)
- [RabbitMQ](configuration.md#rabbitmq)
- [MinIO](configuration.md#s3-bucket)

#### Start the application

Before you can start the application, ensure your dependencies are up and running, and healthy.

Then start the application itself:

```bash
java -jar openaev-api.jar
```

!!! success "Installation done"

    You can now go to [http://localhost:8080](http://localhost:8080) and log in with the credentials configured in your `application.properties` file.

#### Build the application locally

1. cd openaev-front yarn build
2. cp -r builder/prod/* ../openaev-api/src/main/resources/static/
3. cd ../openaev-api
4. mvn clean install -DskipTests
5. create an application.properties based on the existing one in openaev-api and fill all the mandatory fields
6. run java -jar target/openaev-api.jar --spring.config.location=%PATH%\application.properties

## Community contributions

### Helm Charts

<div class="grid cards" markdown>

-   :material-kubernetes:{ .lg .middle } __Kubernetes Helm Charts__

    ---

    OpenAEV Helm Charts for Kubernetes with a global configuration file. More information how to deploy here 
    on [basic installation](https://github.com/devops-ia/helm-openaev/blob/main/charts/openaev/docs/configuration.md)
    and [examples](https://github.com/devops-ia/helm-openaev/blob/main/charts/openaev/docs/examples.md).

    [:material-github:{ .middle } GitHub Repository](https://github.com/devops-ia/helm-openaev/tree/main/charts/openaev)

</div>

### Deploy behind a reverse proxy

If you want to use OpenAEV behind a reverse proxy with a context path, like `https://example.com/openaev`, please change
the `base_path` static parameter.

- `APP__BASE_PATH=/openaev`

By default OpenAEV use websockets so don't forget to configure your proxy for this usage, an example with `Nginx`:

```bash
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
