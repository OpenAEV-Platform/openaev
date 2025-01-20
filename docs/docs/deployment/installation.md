# Installation

All components of OpenBAS are shipped both as [Docker images](https://hub.docker.com/u/openbas) and
manual [installation packages](https://github.com/OpenBAS-Platform/openbas/releases).

!!! note "Production deployment"

    For production deployment, we recommend to deploy all components in containers, including dependencies, using native cloud services or orchestration systems such as [Kubernetes](https://kubernetes.io).

<div class="grid cards" markdown>

-   :simple-docker:{ .lg .middle } __Use Docker__

    ---

    Deploy OpenBAS using Docker and the default `docker-compose.yml` provided
    in the [docker](https://github.com/OpenBAS-Platform/docker).

    [:octicons-arrow-right-24:{ .middle } Setup](#using-docker)

-   :material-package-up:{ .lg .middle } __Manual installation__

    ---

    Deploy dependencies and launch the platform manually using the packages
    released in the [GitHub releases](https://github.com/OpenBAS-Platform/openbas/releases).

    [:octicons-arrow-right-24:{ .middle } Explore](#manual-installation)
</div>

## Using Docker

### Introduction

OpenBAS can be deployed using the *docker compose* command.

### Pre-requisites

**:material-linux:{ .middle } Linux**

```bash
sudo apt install docker-compose
```

**:material-microsoft-windows:{ .middle } Windows and MacOS**

Just download the appropriate [Docker for Desktop](https://www.docker.com/products/docker-desktop) version for your
operating system.

### Clone the repository

Docker helpers are available in the [Docker GitHub repository](https://github.com/OpenBAS-Platform/docker).

```bash
mkdir -p /path/to/your/app && cd /path/to/your/app
git clone https://github.com/OpenBAS-Platform/docker.git
cd docker
```

### Configure the environment

Before running the `docker compose` command, the `docker-compose.yml` file should be configured. By default, the
`docker-compose.yml` file is using environment variables available in the `.env.sample` file.

You can either rename the file `.env.sample` in `.env` and put the expected values or just fill directly the
`docker-compose.yml` with the values corresponding to your environment.

#### Docker compose env

!!! note "Configuration static parameters"

    The complete list of available static parameters is available in the [configuration](configuration.md) section.

Whether you are using one method or the other, here are the mandatory parameters to fill:

```bash
POSTGRES_USER=ChangeMe
POSTGRES_PASSWORD=ChangeMe
KEYSTORE_PASSWORD=ChangeMe
MINIO_ROOT_USER=ChangeMeAccess
MINIO_ROOT_PASSWORD=ChangeMeKey
RABBITMQ_DEFAULT_USER=ChangeMe
RABBITMQ_DEFAULT_PASS=ChangeMe
SPRING_MAIL_HOST=smtp.changeme.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=ChangeMe@domain.com
SPRING_MAIL_PASSWORD=ChangeMe
OPENBAS_MAIL_IMAP_ENABLED=true
OPENBAS_MAIL_IMAP_HOST=imap.changeme.com
OPENBAS_MAIL_IMAP_PORT=993
OPENBAS_ADMIN_EMAIL=ChangeMe@domain.com # Should be a valid email address
OPENBAS_ADMIN_PASSWORD=ChangeMe
OPENBAS_ADMIN_TOKEN=ChangeMe # Should be a valid UUI
COLLECTOR_MITRE_ATTACK_ID=3050d2a3-291d-44eb-8038-b4e7dd107436 # No need for change
COLLECTOR_ATOMIC_RED_TEAM_ID=0f2a85c1-0a3b-4405-a79c-c65398ee4a76 # No need for change
```

If your `docker-compose` deployment does not support `.env` files, just export all environment variables before
launching the platform:

```bash
export $(cat .env | grep -v "#" | xargs)
```

### Persist data

The default for OpenBAS data is to be persistent.

In the `docker-compose.yml`, you will find at the end the list of necessary persistent volumes for the dependencies:

```yaml
volumes:
  esdata:     # ElasticSearch data
  s3data:     # S3 bucket data
  amqpdata:   # RabbitMQ data
```

### Run OpenBAS

#### Using single node Docker

After changing your `.env` file run `docker compose` in detached (-d) mode:

```bash
sudo systemctl start docker.service
# Run docker compose in detached
docker compose up -d
```

#### Using Docker swarm

In order to have the best experience with Docker, we recommend using the Docker stack feature. In this mode you will
have the capacity to easily scale your deployment.

```bash
# If your virtual machine is not a part of a Swarm cluster, please use:
docker swarm init
```

Put your environment variables in `/etc/environment`:

```bash
# If you already exported your variables to .env from above:
sudo cat .env >> /etc/environment
sudo bash -c 'cat .env >> /etc/environment’
sudo docker stack deploy --compose-file docker-compose.yml openbas
```

!!! success "Installation done"

    You can now go to [http://localhost:8080](http://localhost:8080/) and log in with the credentials filled in your configuration.

### OpenBAS X Caldera (Optional part)

You can deploy Caldera alongside OpenBAS to manage agent deployment and execute Caldera scripts.

<div class="grid cards" markdown>

-   :simple-docker:{ .lg .middle } __Use Docker__

    ---

    Deploy Caldera using Docker and the default `docker-compose.yml` provided
    in the [docker](https://github.com/OpenBAS-Platform/caldera/tree/filigran/docker).

    [:octicons-arrow-right-24:{ .middle } Setup](#using-docker)
</div>

Unfortunately, Caldera does not support well environment variables, the `caldera.yml` needs to be modified to change
default API keys and passwords. Only change what is marked as **Change this**, listed below:

!!! note "Caldera application"

    You will never be asked to go into Caldera directly because OpenBAS manages everything for you, so don't hesitate to put the same UUIDv4 in all parameters here.

```yaml
users:
  red:
    red: ChangeMe                                                                     # Change this
  blue:
    blue: ChangeMe                                                                    # Change this
api_key_red: ChangeMe                                                                 # Change this
api_key_blue: ChangeMe                                                                # Change this
api_key: ChangeMe                                                                     # Change this
crypt_salt: ChangeMe                                                                  # Change this
encryption_key: ChangeMe                                                              # Change this
app.contact.http: http://caldera.myopenbas.myorganization.com:8888                    # Change this
app.contact.tunnel.ssh.user_password: ChangeMe                                        # Change this
```

#### Docker compose env

Add this environment variable to connect OpenBAS and Caldera:

```bash
INJECTOR_CALDERA_ENABLE=true
INJECTOR_CALDERA_URL=${CALDERA_URL:-http://caldera:8888}
INJECTOR_CALDERA_PUBLIC_URL=${CALDERA_PUBLIC_URL:-http://localhost:8888}
INJECTOR_CALDERA_API_KEY=${CALDERA_API_KEY:-ChangeMe}
EXECUTOR_CALDERA_ENABLE=true
EXECUTOR_CALDERA_URL=${CALDERA_URL:-http://caldera:8888}
EXECUTOR_CALDERA_PUBLIC_URL=${CALDERA_PUBLIC_URL:-http://localhost:8888}
EXECUTOR_CALDERA_API_KEY=${CALDERA_API_KEY:-ChangeMe}
```

##### Login to Caldera

To connect to Caldera, you need to use one of the users defined in your `caldera.yml` file (either 'red' or 'blue').
OpenBAS will use the red user.

## Manual installation

### Prepare the installation

#### Installation of dependencies

You have to install all the needed dependencies for the main application if you would like to play breach and attack
simulation scenarios. The example below is for Ubuntu:

```bash
sudo apt install openjdk-22-jre
```

#### Download the application files

First, you have to [download and extract the latest release file](https://github.com/OpenBAS-Platform/openbas/releases).

```bash
mkdir /path/to/your/app && cd /path/to/your/app
wget <https://github.com/OpenBAS-Platform/openbas/releases/download/{RELEASE_VERSION}/openbas-release-{RELEASE_VERSION}.tar.gz>
tar xvfz openbas-release-{RELEASE_VERSION}.tar.gz
```

### Install the main platform

#### Configure the application

The main application has just one environment configuration file to change.

```bash
cd openbas
```

Change the *application.properties* file according to your configuration of PostgreSQL, RabbitMQ, Minio and to your
platform.

#### Start the application

Start the Application:

```bash
java -jar openbas-api.jar
```

!!! success "Installation done"

    You can now go to [http://localhost:8080](http://localhost:8080) and log in with the credentials configured in your `application.properties` file.

## Community contributions

### Helm Charts

<div class="grid cards" markdown>

-   :material-kubernetes:{ .lg .middle } __Kubernetes Helm Charts__

    ---

    OpenBAS Helm Charts for Kubernetes with a global configuration file. More information how to deploy here on [basic installation](https://github.com/devops-ia/helm-openbas/blob/main/charts/openbas/docs/configuration.md) and [examples](https://github.com/devops-ia/helm-openbas/blob/main/charts/openbas/docs/examples.md).

    [:material-github:{ .middle } GitHub Repository](https://github.com/devops-ia/helm-openbas/tree/main/charts/openbas)
</div>

### Deploy behind a reverse proxy

If you want to use OpenBAS behind a reverse proxy with a context path, like `https://domain.com/openbas`, please change
the `base_path` static parameter.

- `APP__BASE_PATH=/openbas`

By default OpenBAS use websockets so don't forget to configure your proxy for this usage, an example with `Nginx`:

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

### Additional memory information

OpenBAS platform is based on a JAVA runtime. The application needs at least 4GB of RAM to work properly.

#### PostgreSQL

PostgreSQL is the main database of OpenBAS. You can find more information in
the [official PostgresQL documentation](https://hub.docker.com/_/postgres).

#### MinIO

MinIO is a small process and does not require a high amount of memory. More information are available for Linux here on
the [Kernel tuning guide](https://github.com/minio/minio/tree/master/docs/deployment/kernel-tuning).
