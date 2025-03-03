# Collectors

!!! tip "Tips"

      If you want to learn more about the concept and features of collectors, you can have more info [here](../../usage/collectors.md).

## Installation

### External (Python) collectors

#### Configuration

All external collectors have to be able to access the OpenBAS API. To allow this connection, they have 2 mandatory configuration parameters, the `OPENBAS_URL` and the `OPENBAS_TOKEN`. In addition to these 2 parameters, collectors have other mandatory parameters that need to be set in order to get them work.

!!! info "Collector tokens"

    You can use your administrator token or create another administrator service account to put in your collectors. It is not necessary to have one dedicated user for each collector.

Here is an example of a collector `docker-compose.yml` file:
```yaml
- OPENBAS_URL=http://localhost
- OPENBAS_TOKEN=ChangeMe
- COLLECTOR_ID=ChangeMe # Specify a valid UUIDv4 of your choice 
- "COLLECTOR_NAME=MITRE ATT&CK"
- COLLECTOR_LOG_LEVEL=error
```

Here is an example in a collector `config.yml` file:

```yaml
openbas:
  url: 'http://localhost:3001'
  token: 'ChangeMe'

collector:
  id: 'ChangeMe'
  name: 'MITRE ATT&CK'
  log_level: 'info'
```

## Docker activation

You can either directly run the Docker image of collectors or add them to your current `docker-compose.yml` file.

### Add a collector to your deployment

For instance, to enable the MITRE ATT&CK collector, you can add a new service to your `docker-compose.yml` file:

```docker
  collector-mitre-attack:
    image: openbas/collector-mitre-attack:1.0.0
    environment:
      - OPENBAS_URL=http://localhost
      - OPENBAS_TOKEN=ChangeMe
      - COLLECTOR_ID=ChangeMe
      - "COLLECTOR_NAME=MITRE ATT&CK"
      - COLLECTOR_LOG_LEVEL=error
    restart: always
```

### Launch a standalone collector

To launch standalone collector, you can use the `docker-compose.yml` file of the collector itself. Just download the latest [release](https://github.com/OpenBAS-Platform/collectors/releases) and start the collector:

```
$ wget https://github.com/OpenBAS-Platform/collectors/archive/{RELEASE_VERSION}.zip
$ unzip {RELEASE_VERSION}.zip
$ cd collectors-{RELEASE_VERSION}/mitre-attack/
```

Change the configuration in the `docker-compose.yml` according to the parameters of the platform and of the targeted service. Then launch the collector:

```
$ docker compose up
```

## Manual activation

If you want to manually launch collector, you just have to install Python 3 and pip3 for dependencies:

```
$ apt install python3 python3-pip
```

Download the release of the collectors:

```
$ wget <https://github.com/OpenBAS-Platform/collectors/archive/{RELEASE_VERSION}.zip>
$ unzip {RELEASE_VERSION}.zip
$ cd collectors-{RELEASE_VERSION}/mitre-attack/src/
```

Install dependencies and initialize the configuration:

```
$ pip3 install -r requirements.txt
$ cp config.yml.sample config.yml
```

Change the `config.yml` content according to the parameters of the platform and of the targeted service and launch the collector:

```
$ python3 openbas_mitre.py
```

## Collectors status

The collector status can be displayed in the dedicated section of the platform available in Integration > collectors. You will be able to see the statistics of the RabbitMQ queue of the collector:

![collectors](../assets/collectors-status.png)

!!! bug "Problem"

    If you encounter problems deploying OpenBAS or collectors, you can consult the [troubleshooting page](../troubleshooting.md) page.
