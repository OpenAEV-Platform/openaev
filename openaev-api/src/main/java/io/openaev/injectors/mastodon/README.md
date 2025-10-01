# OpenAEV Mastodon Injector

Table of Contents

- [OpenAEV Mastodon Injector](#openaev-mastodon-injector)
    - [Prerequisites](#prerequisites)
    - [Configuration variables](#configuration-variables)
        - [Base injector environment variables](#base-injector-environment-variables)
    - [Behavior](#behavior)

## Prerequisites

You need a mastodon tenant to be able to use this injector.

## Configuration variables

There are a number of configuration options, which are set either in `docker-compose.yml` (for Docker) or
in `application.properties` (for manual deployment).

### Base injector environment variables

Below are the parameters you'll need to set for running the injector properly:

| Parameter                | application.properties | Docker environment variable | Mandatory | Description                     |
|--------------------------|------------------------|-----------------------------|-----------|---------------------------------|
| Enable Mastodon injector | mastodon.enable        | `MASTODON_ENABLE`           | Yes       | Enable the Mastodon injector.   |
| Mastodon URL             | mastodon.url           | `MASTODON_URL`              | Yes       | The URL of the Mastodon tenant. |

## Behavior

The Mastodon injector is a built-in injector, meaning it is natively included in the platform and can be
enabled using configuration variables.

It allows you to send Mastodon posts onto your Mastodon tenant and use them directly in your OpenAEV scenarios and
simulations.
