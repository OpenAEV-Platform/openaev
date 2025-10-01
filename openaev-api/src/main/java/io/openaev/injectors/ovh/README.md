# OpenAEV OVHCloud SMS Platform Injector

Table of Contents

- [OpenAEV OVHCloud SMS Platform Injector](#openaev-ovhcloud-sms-platform-injector)
    - [Prerequisites](#prerequisites)
    - [Configuration variables](#configuration-variables)
        - [Base injector environment variables](#base-injector-environment-variables)
    - [Deployment](#deployment)
        - [Docker Deployment](#docker-deployment)
        - [Manual Deployment](#manual-deployment)
    - [Behavior](#behavior)

## Prerequisites

You need a valid OVHCloud SMS subscription to be able to use this injector.

## Configuration variables

There are a number of configuration options, which are set either in `docker-compose.yml` (for Docker) or
in `application.properties` (for manual deployment).

### Base injector environment variables

Below are the parameters you'll need to set for running the injector properly:

| Parameter                    | application.properties | Docker environment variable | Mandatory | Description                          |
|------------------------------|------------------------|-----------------------------|-----------|--------------------------------------|
| Enable OVHCloud SMS injector | ovh.sms.enable         | `OVH_SMS_ENABLE`            | Yes       | Enable the OVHCloud SMS injector.    |
| OVHCloud Access Key          | ovh.sms.ak             | `OVH_SMS_AK`                | Yes       | The OVHCloud API access key.         |
| OVHCloud Access Secret       | ovh.sms.as             | `OVH_SMS_AS`                | Yes       | The OVHCloud API secret key.         |
| OVHCloud CK                  | ovh.sms.ck             | `OVH_SMS_CK`                | Yes       | The OVHCloud API CK.                 |
| OVHCloud Service Identifier  | ovh.sms.service        | `OVH_SMS_SERVICE`           | Yes       | The OVHCloud SMS service identifier. |

## Behavior

The OVHCloud SMS Platform injector is a built-in injector, meaning it is natively included in the platform and can be
enabled using configuration variables.

It allows you to send SMS through the OVHCloud SMS services directly in your OpenAEV scenarios and simulations.
