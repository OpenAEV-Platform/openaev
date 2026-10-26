---
last_verified: 2026-10-14
---

# Bulk snapshot export

The bulk snapshot export is a pair of REST API endpoints that return the current, verified state of
every Asset in a Tenant, one page at a time. It is built for the OpenGRC connector and other GRC
(Governance, Risk and Compliance) integrations: instead of replaying every Simulation, the consumer
polls these endpoints and syncs only what changed.

!!! note "Preview feature"

    The bulk snapshot export is a preview feature, off by default.

## Enable the feature

Add `BULK_SNAPSHOT_EXPORT` to the `openaev.enabled-dev-features` property (empty by default), then
restart the platform:

```properties
openaev.enabled-dev-features=BULK_SNAPSHOT_EXPORT
```

The value is a comma-separated list of preview feature names. If the property already has a value,
extend it. The `Access observation snapshots` capability only appears in roles once the feature is
enabled.

## Create the connector account

Create a dedicated, non-administrator user for the connector and grant it the
`Access observation snapshots` capability. The connector authenticates with that user's API key. See
[Users and RBAC](../../administration/users-and-rbac.md).

!!! warning "Decide what the account sees"

    Observations follow the same access rules as their Scenario and Simulation:

    - an administrator, or an account with `Bypass` or `Access assessment`, sees the observations
      of **every** Scenario and Simulation of the Tenant;
    - any other account only sees the observations of the Scenarios and Simulations it has a grant
      on. Without any grant, the attack stream is **empty**, with no error.

    Data that belongs to no Scenario or Simulation is visible to every holder of
    `Access observation snapshots` in the Tenant. Grant this capability only to accounts meant to
    see the whole Tenant inventory.

## Endpoints

```http
POST /api/tenants/{tenantId}/snapshot/attack-observations/search
POST /api/tenants/{tenantId}/snapshot/vulnerability-observations/search
```

The request and response fields are described in Swagger UI, under **Snapshot API**.

## What's next?

- [REST API](rest-api.md) -- Authentication and Swagger UI
- [Users and RBAC](../../administration/users-and-rbac.md) -- Create the connector account and role
- [Configuration](../deployment/configuration.md) -- Set platform properties
