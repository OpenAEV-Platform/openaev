# Upgrade

This page explains how to upgrade OpenAEV. The steps depend on your [installation mode](installation.md).

!!! note "Migrations"

    OpenAEV runs the database migrations on startup. You can upgrade from any version to the latest one, including across several major releases. Check the [breaking changes](../breaking-changes.md) before you upgrade.

!!! warning "Update every instance together"

    If you run several instances, update all of them at the same time for this release: the default agents of the autonomous run move from a platform setting to per-tenant settings, so an instance that still runs the previous version does not read or save the new values.

## Using Docker

Update the image versions in your `docker-compose.yml` file first.

### Single node Docker

```bash
docker compose stop
docker compose pull
docker compose up -d
```

### Docker Swarm

Run this command for each service:

```bash
docker service update --force service_name
```

## Manual installation

Replace all the files with the new release, then restart the platform:

```bash
java -jar openaev-api.jar
```

## What's next?

- [Breaking changes](../breaking-changes.md) -- Changes that need an action during the upgrade
- [Configuration](../../reference/deployment/configuration.md) -- Platform configuration parameters
