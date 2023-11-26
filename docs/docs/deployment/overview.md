# Overview

Before starting the installation, let's discover how OpenEx is working, which dependencies are needed and what are the minimal requirements to deploy it in production.

## Architecture

The OpenEx platform relies on several external databases and serfvices in order to work.

![Architecture](assets/openex_stack.png)

### Platform

The platform is the central part of the OpenEx platform, allowing users to configure exercises and campaigns as well as systems and players.

### Injectors

All injectors are shipped within the platform but are not enabled by default. Injectors are used to interact with third-party applications and services such as social medias, cyber ranges, cybersecurity products, etc.

!!! note "List of connectors"

    You can find all currently available connector in the [OpenEx Ecosystem](https://filigran.notion.site/OpenEx-Ecosystem-30d8eb73d7d04611843e758ddef8941b).

## Infrastructure requirements

### Dependencies

| Component   | Version            | CPU       | RAM          | Disk type                    | Disk space      |
|:------------|:-------------------|:----------| :----------- | :--------------------------- | :-------------- |
| PostgreSQL  | ≥ 16.0            | 2 cores   | ≥ 8GB        | SSD                          | ≥ 16GB          |
| S3 / MinIO  | ≥ RELEASE.2023-02 | 1 core    | ≥ 128MB      | SSD                          | ≥ 16GB          |

### Platform

| Component   | CPU         | RAM          | Disk type                         | Disk space      |
|:------------| :---------- | :----------- | :-------------------------------- | :-------------- |
| OpenEx Core | 2 cores     | ≥ 8GB        | None (stateless)                  | -               |