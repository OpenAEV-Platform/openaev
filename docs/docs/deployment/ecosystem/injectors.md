# Injectors

!!! tip "Tips"

      If you want to learn more about the concept and features of injectors, you can have more info [here](../../usage/injectors.md).

!!! question "Injectors list"

    You are looking for the available injectors? The list is in the [OpenAEV Ecosystem](https://filigran.notion.site/OpenAEV-Ecosystem-30d8eb73d7d04611843e758ddef8941b).

## Installation

### Built-in injectors

Some injectors such as email, SMS, media pressure, etc. are directly embedded into the application. To configure them, 
just add the proper configuration parameters in your platform configuration.

### External (Python) injectors

There are multiple ways to deploy an external injectors from OpenAEV:

- Integration Manager (Recommended)
- Docker deployment
- Manual deployment

!!! info

    ⚠️ All external injectors must be able to access the OpenAEV API. They require 2 mandatory configuration parameters: OPENAEV_URL and OPENAEV_TOKEN. In addition, each collector has specific mandatory parameters that need to be configured.

#### Integration Manager (Recommended)
The easiest way to deploy injectors is through the Integration Manager, which allows automatic deployment directly from the OpenAEV interface.

👉 See the [Integration Manager documentation](integration-manager/overview.md) for detailed instructions.


#### Docker Deployment
Several options are available for Docker deployment:

##### Add an injector to your existing deployment
For instance, to enable the HTTP query injector, you can add a new service to your `docker-compose.yml` file:

```docker
  injector-http-query:
    image: openaev/injector-http-query:latest
    environment:
      - OPENAEV_URL=http://localhost
      - OPENAEV_TOKEN=ChangeMe
      - INJECTOR_ID=ChangeMe
      - "INJECTOR_NAME=HTTP query"
      - INJECTOR_LOG_LEVEL=error
    restart: always
```
Note: Injector images and available versions can be found on Docker Hub.

##### Launch a standalone collector
To launch standalone injector, you can use the `docker-compose.yml` file of the injector itself. Just download the latest [release](https://github.com/OpenAEV-Platform/injectors/releases) and start the injector:

```
$ wget https://github.com/OpenAEV-Platform/injectors/archive/{RELEASE_VERSION}.zip
$ unzip {RELEASE_VERSION}.zip
$ cd injectors-{RELEASE_VERSION}/http-query/
```

Change the configuration in the `docker-compose.yml` according to the parameters of the platform and of the targeted service. Then launch the injector:

```
$ docker compose up
```

#### Manual activation

If you want to manually launch injector, you just have to install Python 3 and pip3 for dependencies:

```
$ apt install python3 python3-pip
```

Download the release of the injectors:

```
$ wget <https://github.com/OpenAEV-Platform/injectors/archive/{RELEASE_VERSION}.zip>
$ unzip {RELEASE_VERSION}.zip
$ cd injectors-{RELEASE_VERSION}/http-query/src/
```

Install dependencies and initialize the configuration:

```
$ pip3 install -r requirements.txt
$ cp config.yml.sample config.yml
```

Change the `config.yml` content according to the parameters of the platform and of the targeted service.
For example :
```yaml
openaev:
  url: 'http://localhost:3001'
  token: 'ChangeMe'

injector:
  id: 'ChangeMe'
  name: 'HTTP query'
  log_level: 'info'
```

Finally :  launch the injector:

```
$ python3 openaev_http.py
```

#### Configuration

All external injectors have to be able to access the OpenAEV API. To allow this connection, they have 2 mandatory configuration parameters, the `OPENAEV_URL` and the `OPENAEV_TOKEN`. In addition to these 2 parameters, injectors have other mandatory parameters that need to be set in order to get them work.

!!! info "Injector tokens"

    You can use your administrator token or create another administrator service account to put in your injectors. It is not necessary to have one dedicated user for each injector.

Here is an example of a injector `docker-compose.yml` file:
```yaml
- OPENAEV_URL=http://localhost
- OPENAEV_TOKEN=ChangeMe
- INJECTOR_ID=ChangeMe # Specify a valid UUIDv4 of your choice
- "INJECTOR_NAME=HTTP query"
- INJECTOR_LOG_LEVEL=error
```

Here is an example in a injector `config.yml` file:

```yaml
openaev:
  url: 'http://localhost:3001'
  token: 'ChangeMe'

injector:
  id: 'ChangeMe'
  name: 'HTTP query'
  log_level: 'info'
```

#### Networking

Be aware that all injectors are reaching RabbitMQ based the RabbitMQ configuration provided by the OpenAEV platform. The injector must be able to reach RabbitMQ on the specified hostname and port. If you have a specific Docker network configuration, please be sure to adapt your `docker-compose.yml` file in such way that the injector container gets attached to the OpenAEV Network, e.g.:

```yaml
networks:
  default:
    external: true
    name: openaev-docker_default
```

## Injectors status

The injector status can be displayed in the dedicated section of the platform available in Integration > injectors. You will be able to see the statistics of the RabbitMQ queue of the injector:

![injectors](../assets/injectors-status.png)
