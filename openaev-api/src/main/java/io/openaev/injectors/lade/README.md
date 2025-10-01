# OpenAEV Airbus LADE Injector

Table of Contents

- [OpenAEV Airbus LADE Injector](#openaev-airbus-lade-injector)
    - [Prerequisites](#prerequisites)
    - [Configuration variables](#configuration-variables)
        - [Base injector environment variables](#base-injector-environment-variables)
    - [Behavior](#behavior)

## Prerequisites

You need an Airbus Lade instance to be able to use this injector.

## Configuration variables

There are a number of configuration options, which are set either in `docker-compose.yml` (for Docker) or
in `application.properties` (for manual deployment).

### Base injector environment variables

Below are the properties you'll need to set for OpenAEV:

| Parameter                   | application.properties | Docker environment variable | Mandatory | Description                              |
|-----------------------------|------------------------|-----------------------------|-----------|------------------------------------------|
| Enable Airbus LADE injector | lade.enable            | `LADE_ENABLE`               | Yes       | Enable the Airbus LADE injector.         |
| Airbus LADE URL             | lade.url               | `LADE_URL`                  | Yes       | The URL of the Airbus LADE tenant.       |
| Session duration            | lade.session           | `LADE_SESSION`              | No        | The duration of the session (technical). |
| Airbus LADE username        | lade.username          | `LADE_USERNAME`             | Yes       | The Airbus LADE tenant username.         |
| Airbus LADE password        | lade.password          | `LADE_PASSWORD`             | Yes       | The Airbus LADE tenant password.         |

## Behavior

The Airbus LADE injector is a built-in injector, meaning it is natively included in the platform and can be enabled
using configuration variables.

It allows you to use the various capabilities from the Airbus LADE directly in your OpenAEV scenarios and simulations.
