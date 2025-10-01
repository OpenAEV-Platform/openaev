# OpenAEV Caldera Injector

Table of Contents

- [OpenAEV Caldera Injector](#openaev-caldera-injector)
    - [Prerequisites](#prerequisites)
    - [Configuration variables](#configuration-variables)
        - [Base injector environment variables](#base-injector-environment-variables)
    - [Behavior](#behavior)

## Prerequisites

You need a caldera instance to be able to use this injector.

## Configuration variables

There are a number of configuration options, which are set either in `docker-compose.yml` (for Docker) or
in `application.properties` (for manual deployment).

### Base injector environment variables

Below are the parameters you'll need to set for running the injector properly:

| Parameter               | application.properties      | Docker environment variable   | Mandatory | Description                                              |
|-------------------------|-----------------------------|-------------------------------|-----------|----------------------------------------------------------|
| Enable Caldera injector | injector.caldera.enable     | `INJECTOR_CALDERA_ENABLE`     | Yes       | Enable the Caldera.                                      |
| Injector ID             | injector.caldera.id         | `INJECTOR_CALDERA_ID`         | Yes       | A unique `UUIDv4` identifier for this injector instance. |
| Caldera internal URL    | injector.caldera.url        | `INJECTOR_CALDERA_URL`        | Yes       | Internal URL of your Caldera instance.                   |
| Caldera public URL      | injector.caldera.public-url | `INJECTOR_CALDERA_PUBLIC_URL` | Yes       | Public URL of your Caldera instance.                     |
| Caldera Api Key         | injector.caldera.api-key    | `INJECTOR_CALDERA_API_KEY`    | Yes       | Api Key for communicate to your Caldera instance.        |

## Behavior

The Caldera injector is a built-in injector, meaning it is natively included in the platform and can be
enabled using configuration variables.

It allows you to collect all abilities from your caldera instance and use them directly in your OpenAEV scenarios and
simulations.
