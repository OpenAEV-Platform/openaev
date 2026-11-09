# Upgrade

This page explains how to upgrade OpenAEV. The steps depend on your [installation mode](installation.md).

!!! note "Migrations"

    OpenAEV runs the database migrations on startup. You can upgrade from any version to the latest one, including across several major releases. Check the [breaking changes](../breaking-changes.md) before you upgrade.

!!! warning "Update every instance together"

    If you run several instances, update all of them at the same time for this release: the default agents of the autonomous run move from a platform setting to per-tenant settings, so an instance that still runs the previous version does not read or save the new values.

    The existing values are copied to every tenant that exists when the upgrade runs. A tenant created afterwards starts with the built-in default until an administrator sets its own.

!!! warning "XTM Composer needs the platform settings write right"

    XTM Composer registers itself with `POST /api/xtm-composer/register` and refreshes its connectivity with `PUT /api/xtm-composer/{id}/refresh-connectivity`. Both calls require the platform settings write right, on the platform route and on the tenant route. A token user that holds only the catalog write right gets a 403, and the composer stays unregistered.

    Run XTM Composer with an administrator token, or give its user the platform settings write right.

!!! warning "Name the tenant when you read or save the autonomous run default agents"

    `GET` and `PUT /api/autonomous-runs/default-agents` act on one tenant. A call that names no tenant answers 400 for a caller that belongs to several tenants without the default one, and for a caller that belongs to no tenant. A caller of one tenant, or of several tenants including the default one, gets that tenant without naming it.

    Name the tenant in the path (`/api/tenants/{tenantId}/autonomous-runs/default-agents`) or in the `X-Tenant-Ids` header. The user interface already does.

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
