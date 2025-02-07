# Injectors

!!! tip "Tips"

      If you want to learn more about the concept and features of injectors, you can have more info [here](../../usage/injectors.md).

!!! question "Injectors list"

    You are looking for the available injectors? The list is in the [OpenBAS Ecosystem](https://filigran.notion.site/OpenBAS-Ecosystem-30d8eb73d7d04611843e758ddef8941b).

## Installation

### Built-in injectors

Some injectors such as email, SMS, media pressure, etc. are directly embedded into the application. To configure them, 
just add the proper configuration parameters in your platform configuration.

### External (Python) injectors

#### Configuration

All external injectors have to be able to access the OpenBAS API. To allow this connection, they have 2 mandatory configuration parameters, the `OPENBAS_URL` and the `OPENBAS_TOKEN`. In addition to these 2 parameters, injectors have other mandatory parameters that need to be set in order to get them work.

!!! info "Injector tokens"

    You can use your administrator token or create another administrator service account to put in your injectors. It is not necessary to have one dedicated user for each injector.

Here is an example of a injector `docker-compose.yml` file:
```yaml
- OPENBAS_URL=http://localhost
- OPENBAS_TOKEN=ChangeMe
- INJECTOR_ID=ChangeMe # Specify a valid UUIDv4 of your choice
- "INJECTOR_NAME=HTTP query"
- INJECTOR_LOG_LEVEL=error
```

Here is an example in a injector `config.yml` file:

```yaml
openbas:
  url: 'http://localhost:3001'
  token: 'ChangeMe'

injector:
  id: 'ChangeMe'
  name: 'HTTP query'
  log_level: 'info'
```

#### Networking

Be aware that all injectors are reaching RabbitMQ based the RabbitMQ configuration provided by the OpenBAS platform. The injector must be able to reach RabbitMQ on the specified hostname and port. If you have a specific Docker network configuration, please be sure to adapt your `docker-compose.yml` file in such way that the injector container gets attached to the OpenBAS Network, e.g.:

```yaml
networks:
  default:
    external: true
    name: openbas-docker_default
```

## Docker activation

You can either directly run the Docker image of injectors or add them to your current `docker-compose.yml` file.

### Add an injector to your deployment

For instance, to enable the HTTP query injector, you can add a new service to your `docker-compose.yml` file:

```docker
  injector-http-query:
    image: openbas/injector-http-query:latest
    environment:
      - OPENBAS_URL=http://localhost
      - OPENBAS_TOKEN=ChangeMe
      - INJECTOR_ID=ChangeMe
      - "INJECTOR_NAME=HTTP query"
      - INJECTOR_LOG_LEVEL=error
    restart: always
```

### Launch a standalone injector

To launch standalone injector, you can use the `docker-compose.yml` file of the injector itself. Just download the latest [release](https://github.com/OpenBAS-Platform/injectors/releases) and start the injector:

```
$ wget https://github.com/OpenBAS-Platform/injectors/archive/{RELEASE_VERSION}.zip
$ unzip {RELEASE_VERSION}.zip
$ cd injectors-{RELEASE_VERSION}/http-query/
```

Change the configuration in the `docker-compose.yml` according to the parameters of the platform and of the targeted service. Then launch the injector:

```
$ docker compose up
```

## Manual activation

If you want to manually launch injector, you just have to install Python 3 and pip3 for dependencies:

```
$ apt install python3 python3-pip
```

Download the release of the injectors:

```
$ wget <https://github.com/OpenBAS-Platform/injectors/archive/{RELEASE_VERSION}.zip>
$ unzip {RELEASE_VERSION}.zip
$ cd injectors-{RELEASE_VERSION}/http-query/src/
```

Install dependencies and initialize the configuration:

```
$ pip3 install -r requirements.txt
$ cp config.yml.sample config.yml
```

Change the `config.yml` content according to the parameters of the platform and of the targeted service and launch the injector:

```
$ python3 openbas_http.py
```

## Injectors status

The injector status can be displayed in the dedicated section of the platform available in Integration > injectors. You will be able to see the statistics of the RabbitMQ queue of the injector:

![injectors](../assets/injectors-status.png)

!!! bug "Problem"

    If you encounter problems deploying OpenBAS or injectors, you can consult the [troubleshooting page](../troubleshooting.md) page.
