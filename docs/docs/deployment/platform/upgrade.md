# Upgrade

This page explains how to upgrade OpenAEV. The steps depend on your [installation mode](installation.md).

!!! note "Migrations"

    OpenAEV runs the database migrations on startup. You can upgrade from any version to the latest one, including across several major releases. Check the [breaking changes](../breaking-changes.md) before you upgrade.

!!! warning "Stop every instance of the previous version first"

    Stop every instance of the previous version before the first instance of this version starts. The migrations of this version make the platform settings writes of the previous version fail with SQLSTATE 42501: the administrator settings, the SMTP and IMAP health flags, the IMAP read position, the banners, the XTM Hub flag and the default agents of the autonomous run. An instance of the previous version that runs next to an instance of this version cannot save any of them, so a rolling update is not supported.

    The default agents of the autonomous run move from a platform setting to per-tenant settings. The existing values are copied to every tenant that exists when the upgrade runs. A tenant created afterwards starts with the built-in default until an administrator sets its own.

!!! warning "Rolling back needs the database backup"

    Going back to the previous version means restoring the database backup taken before the upgrade. The previous version cannot write its platform settings on the upgraded database, and the upgrade deletes the platform settings that held the default agents and discovery modes of the autonomous run, which are the only ones the previous version reads. Take the backup before you start the first instance of this version.

!!! warning "XTM Composer needs the platform settings write right"

    XTM Composer registers itself with `POST /api/xtm-composer/register` and refreshes its connectivity with `PUT /api/xtm-composer/{id}/refresh-connectivity`. Both calls require the platform settings write right. A token user that holds only the catalog write right gets a 403, and the composer stays unregistered.

    A deployment that passes the administrator token (`OPENAEV_ADMIN_TOKEN`) to XTM Composer as `OPENAEV__TOKEN`, as the Docker Compose file of the OpenAEV repository (`openaev-dev/docker-compose.yml`) does, keeps working: the administrator holds the right. A deployment that set a custom token for XTM Composer gives the user of that token the platform settings write right (`MANAGE_PLATFORM_SETTINGS`). A user that belongs to no tenant calls the platform route; the tenant route (`/api/tenants/{tenantId}/xtm-composer/...`) also requires membership of the tenant it names.

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

Scale every OpenAEV service to zero replicas before the update, so that no instance of the previous version runs next to the first instance of this version (see the warning at the top of this page). Then run this command for each service and scale the services back up:

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
