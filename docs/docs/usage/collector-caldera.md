# Caldera collector

The integration between an OpenEx instance and a Caldera instance allows you to enrich assets data.

## Configuration variables

Below are the properties you'll need to set for OpenEx:

| Property                 | application.properties     | Docker environment variable  | Mandatory | Description                                                                         |
|--------------------------|----------------------------|------------------------------|-----------|-------------------------------------------------------------------------------------|
| Enable Caldera collector | collector.caldera.enable   | `COLLECTOR.CALDERA.ENABLE`   | Yes       | Enable the Caldera collector.                                                       |
| Collector ID             | collector.caldera.id       | `COLLECTOR.CALDERA.ID`       | Yes       | The ID of the collector.                                                            |
| Caldera URL              | collector.caldera.url      | `COLLECTOR.CALDERA.URL`      | Yes       | The URL of the Caldera instance.                                                    |
| Caldera API Key          | collector.caldera.api-key  | `COLLECTOR.CALDERA.API-KEY`  | Yes       | The API Key for the rest API of the Caldera instance.                               |
| Caldera polling interval | collector.caldera.interval | `COLLECTOR.CALDERA.INTERVAL` | No        | The time interval in seconds where the collect is triggered. Default is 60 seconds. |

## Behavior

Each interval, a job retrieves the deployed agents on Caldera and populates the OpenEx database by creating Assets.

Deduplication is done thanks to the caldera `paw` property :

- if there is no asset on OpenEx based on this `paw`, a new asset is created
- if there is an asset on OpenEx based on this `paw` and the source of creation is only Caldera, this asset is updated

There is no automatic deletion of OpenEx assets if Caldera agents no longer exist.

#### Mapping

| Agent Property  | Asset Property |
|-----------------|----------------|
| paw             | externalId     |
| paw             | name           |
| host_ip_addrs   | ips            |
| platform        | platform       |
| last_seen       | lastSeen       |
