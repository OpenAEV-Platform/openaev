!!! tip "Prerequisites"

    Ensure you have followed the steps for installing prerequisites according to your development
    platform of choice:

    * [Linux (Ubuntu used as example)](/development/environment_ubuntu)
    * [Windows](/development/environment_windows)
    * MacOS (TBD)

# Building and running from source

!!! Info "Assumed shell location"

    This documentation assumes that commands listed here are run from the root
    folder of the git repository, unless stated otherwise.

## Clone the OpenBAS repository
Obtain a clone of the main OpenBAS repository and navigate to it:
```shell
git clone https://github.com/OpenBAS-Platform/openbas
cd openbas
```

## Backend

### Configuring
Development assumes that you are using a development-specific properties file. The file located at
`./openbas-api/src/main/resources/application.properties` is a version-controlled example file and
lacks many of the required configuration settings needed for OpenBAS to execute.

It is strongly recommended to make a copy of the sample `application.properties` file to create
a development-specific profile called `dev`.

Copy and paste the example `application.properties` file into the same directory:
```shell
cp ./openbas-api/src/main/resources/application.properties ./openbas-api/src/main/resources/application-dev.properties
```

#### Required dependencies

**Start the development dependencies docker stack**

Preconfigured containers for all the needed support containers (PostgreSQL, MinIO, RabbitMQ, Elasticsearch...)
can be found as a docker compose file in `./openbas/openbas-dev`.

Create a file a this location: `./openbas/openbas-dev/.env` and populate it with a minimal set of keys:
```shell
POSTGRES_USER=openbas	
POSTGRES_PASSWORD=openbas
KEYSTORE_PASSWORD=minioadmin
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
RABBITMQ_DEFAULT_USER=rbit
RABBITMQ_DEFAULT_PASS=rbitpass
```
Note: these are example values, but will do in a development environment. Available environment variables
can be examined in `./openbas/openbas-dev/docker-compose.yml`.

Then start the stack:
```shell
cd ./openbas/openbas-dev
docker compose up -d
```

**Set up the local development configuration for the OpenBAS server**

Edit the `application-dev.properties` file, according to the .env file created earlier,
and any additional configuration. Make sure the file contains settings for at the very minimum
the following dependencies:

- PostgreSQL
- MinIO
- RabbitMQ
- Engine (Elasticsearch or OpenSearch)

All required settings are listed in the [Configuration documentation](/deployment/configuration#dependencies)


### Building and running
Maven is used for package management and building the main server binary.
OpenBAS is a Spring Boot application and thus can be built and started
in one fell swoop with
```shell
mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.main-class=io.openbas.App
```

!!! tip "IntelliJ IDEA run configuration"

    The OpenBAS repository provides predefined IntelliJ IDEA run configurations for
    both the backend. After loading the OpenBAS cloned repository's root
    directory in IDEA, the "Backend start" run configuration will show up in the Run
    widget in the top right corner.


## Frontend
!!! Info "Change the location of your shell"

    In this section, commands need to be run from a subfolder: ./openbas-front

Navigate to `./openbas-front`.
```shell
cd ./openbas-front
```

### Building
Execute `yarn install` to fetch all dependencies from npmjs.com:
```shell
yarn install
```

### Running
Execute `yarn start` to start a frontend locally:
```shell
yarn start
```
The banner should come up soon after:
```
  VITE v6.3.4  ready in 168 ms

  ➜  Local:   http://localhost:3001/
  ➜  Network: use --host to expose
  ➜  press h + enter to show help
```
It is possible to navigate to http://localhost:3001/ by default. Note that the backend needs
to be running otherwise the GUI will not come up in the browser.

!!! tip "IntelliJ IDEA run configuration"

    The OpenBAS repository provides predefined IntelliJ IDEA run configurations for
    both the frontend. After loading the OpenBAS cloned repository's root
    directory in IDEA, the "Frontend start" run configuration will show up in the Run
    widget in the top right corner.
