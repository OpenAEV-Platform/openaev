# Installation

All components of OpenEx are shipped both as [Docker images](https://hub.docker.com/u/openexhq) and manual [installation packages](https://github.com/OpenEx-Platform/openex/releases).

!!! note "Production deployment"

    For production deployment, we recommend to deploy all components in containers, including dependencies, using native cloud services or orchestration systems such as [Kubernetes](https://kubernetes.io).

<div class="grid cards" markdown>

-   :simple-docker:{ .lg .middle } __Use Docker__

    ---

    Deploy OpenEx using Docker and the default `docker-compose.yml` provided
    in the [docker](https://github.com/OpenEx-Platform/docker).

    [:octicons-arrow-right-24:{ .middle } Setup](#using-docker)

-   :material-package-up:{ .lg .middle } __Manual installation__

    ---

    Deploy dependencies and launch the platform manually using the packages
    released in the [GitHub releases](https://github.com/OpenEx-Platform/openex/releases).

    [:octicons-arrow-right-24:{ .middle } Explore](#manual-installation)
</div>

## Using Docker

### Introduction

OpenEx can be deployed using the *docker-compose* command.

### Pre-requisites

**:material-linux:{ .middle } Linux**

```bash
$ sudo apt install docker-compose
```

**:material-microsoft-windows:{ .middle } Windows and MacOS**

Just download the appropriate [Docker for Desktop](https://www.docker.com/products/docker-desktop) version for your operating system.

### Clone the repository

Docker helpers are available in the [Docker GitHub repository](https://github.com/OpenEx-Platform/docker).

```bash
$ mkdir -p /path/to/your/app && cd /path/to/your/app
$ git clone https://github.com/OpenEx-Platform/docker.git
$ cd docker
```

### Configure the environment

Before running the `docker-compose` command, the `docker-compose.yml` file should be configured. By default, the `docker-compose.yml` file is using environment variables available in the file `.env.sample`.

You can either rename the file `.env.sample` in `.env` and put the expected values or just fill directly the `docker-compose.yml` with the values corresponding to your environment.

!!! note "Configuration static parameters"

    The complete list of available static parameters is available in the [configuration](configuration.md) section.

Whether you are using one method or the other, here are the mandatory parameters to fill:

```bash
POSTGRES_USER=ChangeMe
POSTGRES_PASSWORD=ChangeMe
KEYSTORE_PASSWORD=ChangeMe
MINIO_ROOT_USER=ChangeMeAccess
MINIO_ROOT_PASSWORD=ChangeMeKey
SPRING_MAIL_HOST=smtp.changeme.com
SPRING_MAIL_PORT=25
SPRING_MAIL_USERNAME=ChangeMe@domain.com
SPRING_MAIL_PASSWORD=ChangeMe
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST=*
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
# Should be remove if you have already an admin user and won't update it
OPENEX_ADMIN_EMAIL=ChangeMe
OPENEX_ADMIN_PASSWORD=ChangeMe
OPENEX_ADMIN_TOKEN=ChangeMe
```

If your `docker-compose` deployment does not support `.env` files, just export all environment variables before launching the platform:

```bash
$ export $(cat .env | grep -v "#" | xargs)
```

### Memory management settings

#### PostgreSQL

PostgreSQL is the main database of OpenEx. You can find more information in the [official PostgresQL documentation](https://hub.docker.com/_/postgres).

#### MinIO

MinIO is a small process and does not require a high amount of memory. More information are available for Linux here on the [Kernel tuning guide](https://github.com/minio/minio/tree/master/docs/deployment/kernel-tuning).

### Persist data

The default for OpenEx data is to be persistent.

In the `docker-compose.yml`, you will find at the end the list of necessary persitent volumes for the dependencies:

```yaml
volumes:
  pgsqldata:
  s3data:
```

### Run OpenEx

#### Using single node Docker

After changing your `.env` file run `docker-compose` in detached (-d) mode:

```bash
$ sudo systemctl start docker.service
# Run docker-compose in detached 
$ docker-compose up -d
```

#### Using Docker swarm

In order to have the best experience with Docker, we recommend using the Docker stack feature. In this mode you will have the capacity to easily scale your deployment.

```bash
# If your virtual machine is not a part of a Swarm cluster, please use:
$ docker swarm init
```

Put your environment variables in `/etc/environment`:

```bash
# If you already exported your variables to .env from above:
$ sudo cat .env >> /etc/environment
$ sudo bash -c 'cat .env >> /etc/environment’
$ sudo docker stack deploy --compose-file docker-compose.yml openex
```

!!! success "Installation done"

    You can now go to [http://localhost:8080](http://localhost:8080/) and log in with the credentials:

    - Login: [admin@openex.io](mailto:admin@openex.io)
    - Password: admin

## Manual installation

### Prepare the installation

#### Installation of dependencies

You have to install all the needed dependencies for the main application and the workers. The example below if for Ubuntu:

```bash
$ sudo apt install openjdk-18-jre 
```

#### Download the application files

First, you have to [download and extract the latest release file](https://github.com/OpenEx-Platform/openex/releases).

```bash
$ mkdir /path/to/your/app && cd /path/to/your/app
$ wget <https://github.com/OpenEx-Platform/openex/releases/download/{RELEASE_VERSION}/openex-release-{RELEASE_VERSION}.tar.gz>
$ tar xvfz openex-release-{RELEASE_VERSION}.tar.gz
```

### Install the main platform

#### Configure the application

The main application has just one environment configuration file to change.

```bash
$ cd openex
```

Change the *application.properties* file according to your configuration of PostgreSQL and Minio and to your platform.

#### Start the application

Start the Application:

```bash
$ java -jar openex-api.jar
```

You should now be able to access to the platform using the IP and the port defined in the configuration (by default: http://localhost:8080).

The default username and password are:

- Login: admin@openex.io
- Password: admin
