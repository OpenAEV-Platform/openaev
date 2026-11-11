# Upgrade

This page explains how to upgrade OpenAEV. The steps depend on your [installation mode](installation.md).

!!! note "Migrations"

    OpenAEV runs the database migrations on startup. You can upgrade from any version to the latest one, including across several major releases. Check the [breaking changes](../breaking-changes.md) before you upgrade.

!!! warning "Instances of the previous version cannot save platform settings"

    Once the first instance of this version has run its migrations, the database refuses the platform settings writes of an instance of the previous version (SQLSTATE 42501): the administrator settings, the SMTP and IMAP health flags, the IMAP read position, the banners, the XTM Hub flag and the default agents of the autonomous run. During a rolling update, these writes fail on the instances of the previous version until they stop, usually a few minutes; tenant data is not affected, and an administrator whose save fails saves again once the update is complete. To avoid that window, stop every instance of the previous version before the first instance of this version starts.

    The default agents of the autonomous run move from a platform setting to per-tenant settings. The existing values are copied to every tenant that exists when the upgrade runs. A tenant created afterwards starts with the built-in default until an administrator sets its own.

!!! warning "Rolling back to the previous version"

    Take a database backup before the first instance of this version starts. To go back to the previous version, restore that backup. If you go back without restoring it, first remove the trigger that refuses the platform settings writes of the previous version:

    ```sql
    DROP TRIGGER parameters_guard_platform_row ON parameters;
    ```

    Without the backup, the previous version also reads the built-in default agents and discovery modes of the autonomous run: the upgrade moved them to per-tenant settings, and the previous version reads only the platform setting.

!!! warning "XTM Composer needs the platform settings write right"

    XTM Composer registers itself with `POST /api/xtm-composer/register` and refreshes its connectivity with `PUT /api/xtm-composer/{id}/refresh-connectivity`. Both calls require the platform settings write right. A token user that holds only the catalog write right gets a 403, and the composer stays unregistered.

    A deployment that passes the administrator token (`OPENAEV_ADMIN_TOKEN`) to XTM Composer as `OPENAEV__TOKEN`, as the Docker Compose file of the OpenAEV repository (`openaev-dev/docker-compose.yml`) does, keeps working: the administrator holds the right. A deployment that set a custom token for XTM Composer gives the user of that token the platform settings write right (`MANAGE_PLATFORM_SETTINGS`). A user that belongs to no tenant calls the platform route; the tenant route (`/api/tenants/{tenantId}/xtm-composer/...`) also requires membership of the tenant it names.

!!! warning "Name the tenant when you read or save the autonomous run default agents"

    `GET` and `PUT /api/autonomous-runs/default-agents` act on one tenant. A call that names no tenant answers 400 for a caller that belongs to several tenants without the default one, and for a caller that belongs to no tenant. A caller of one tenant, or of several tenants including the default one, gets that tenant without naming it.

    Name the tenant in the path (`/api/tenants/{tenantId}/autonomous-runs/default-agents`) or in the `X-Tenant-Ids` header. The user interface already does.

!!! warning "Name the tenant when you call the contract endpoints"

    `GET`, `PUT`, `PUT .../mapping` and `DELETE` on `/api/injector_contracts/{id}`, and `GET` on `/api/threat_arsenals/{id}`, act on one tenant's copy of a contract. A built-in contract exists in every tenant.

    - A caller of several tenants that include the default one, with no selector, acts on the default tenant's copy, also when it meant another one.
    - A caller of several tenants without the default one, with no selector, gets 400 (`TENANT_SELECTOR_REQUIRED`).
    - A selector that names several tenants, or a caller that belongs to no tenant, gets 400 (`TENANT_SINGLE_SCOPE_REQUIRED`).
    - A `DELETE` of a contract that belongs to another tenant than the one of the request gets 404, and the contract is kept.

    Send `X-Tenant-Ids` with the one tenant or use `/api/tenants/{tenantId}/...`. The user interface already does.

!!! warning "Rights on contracts count where they are held"

    A contract copy appears in the search, the count, the facets and the raw list of a caller, and opens by id, only when the right comes from a group of the tenant of that copy. A `THREAT_ARSENAL` grant also counts when a platform group holds it. The `ACCESS_THREAT_ARSENALS` capability and the tenant bypass count only from a group of the tenant of the copy. A caller whose right sits in a group of another tenant no longer sees that tenant's copy.

    On the regular path, the by-id check also reads the default tenant (see [tenant isolation](../../development/tenant-isolation.md)). With `X-Tenant-Ids: A`, a caller whose right is held only in a group of tenant A sees A's copy in the search and gets 403 on `GET /api/injector_contracts/{id}`. On `/api/tenants/{A}/...` both agree.

## Using Docker

Update the image versions in your `docker-compose.yml` file first.

### Single node Docker

```bash
docker compose stop
docker compose pull
docker compose up -d
```

### Docker Swarm

Run this command for each service (see the warnings at the top of this page for the instances of the previous version during the update):

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
