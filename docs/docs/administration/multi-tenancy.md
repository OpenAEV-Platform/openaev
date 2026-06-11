# Multi-tenancy

!!! tip "Under construction"

    This feature is under development.

{% if "multi-tenancy" in (config.extra.feature_flags.enabled|string|lower|replace(" ", "")).split(",") %}
This page explains how to configure and manage multi-tenancy in OpenAEV, enabling multiple isolated workspaces on a single platform instance.

!!! tip "Enterprise Edition"

    Multi-tenancy is an **OpenAEV Enterprise Edition** feature. A valid EE license is required to create and manage tenants.
    See [Enterprise Edition](enterprise.md) for activation instructions.

---

## What is multi-tenancy?

Multi-tenancy allows a single OpenAEV instance to host multiple **isolated workspaces**, called tenants. Each tenant has its own data, users, roles, groups, integrations, and infrastructure, completely separated from other tenants on the same platform.

## Why use multi-tenancy?

Multi-tenancy is the recommended deployment model for:

- **MSSPs** managing multiple customers from a single platform
- **Large organizations** isolating business units or subsidiaries

It reduces operational overhead by centralizing infrastructure while guaranteeing strict data isolation between tenants.

---

## Concepts

### Tenant

A tenant is a fully isolated workspace within the OpenAEV platform. It contains:

- Its own **users, groups, and roles**
- Its own **scenarios, simulations, and atomic tests**
- Its own **assets, teams, and players**
- Its own **integrations** (injectors, collectors, executors)

Data from one tenant is never visible to users of another tenant.

### Platform level vs. Tenant level

OpenAEV distinguishes two levels of administration:

- The **platform level** is where you manage tenants, platform-wide users, roles, and groups. Platform administrators operate at this level to create tenants, assign users across tenants, and configure global settings.
- The **tenant level** is where day-to-day work happens: scenarios, simulations, assets, findings, integrations. Each tenant has its own users, groups, and roles that are independent from other tenants.

A user or group can exist at both levels. For example, a platform administrator manages tenants globally but must be explicitly added to a tenant group to access that tenant's data.

---

## Managing tenants

Manage tenants from **Settings → Tenants**. You need the `Manage platform settings` capability (platform administrator).

From this page you can create, edit, and delete tenants. When you create a tenant, it is immediately active and all built-in integrations (injectors, collectors) are automatically registered for it.

### Soft-delete and reactivation

Tenant deletion is a **soft-delete** operation. The tenant and all its data are retained for **30 days** before permanent purge. During this period, you can reactivate the tenant from the same page.

!!! danger "Permanent deletion after 30 days"

    After 30 days, the tenant and **all its data** (scenarios, simulations, assets, findings, documents) are permanently purged and cannot be recovered.

---

## Users and access in a multi-tenant setup

### Assigning users to a tenant

You assign a user to a tenant directly from the tenant's user management. Once assigned, the user's permissions within that tenant are determined by the groups and roles they belong to in that tenant context.

A user can belong to **multiple tenants** simultaneously. Permissions are evaluated independently in each tenant context.

---

## SSO and tenant mapping

When using Single Sign-On (OpenID Connect or SAML2), you can automatically assign users to a specific tenant at login using the `tenant_id` parameter.

### Configuration

Add the following property for your SSO provider registration:

| Parameter | Environment variable | Description |
|---|---|---|
| `openaev.provider.{registrationId}.tenant_id` | `OPENAEV_PROVIDER_{registrationId}_TENANT_ID` | Tenant ID to assign users to when they log in via this SSO provider |

### Example

Map an Azure AD SAML2 provider to a specific tenant:

```properties
OPENAEV_PROVIDER_AZURE_TENANT_ID=<your-tenant-uuid>
```

---

## What's next?

- [Users and RBAC](users-and-rbac.md) — Configure roles and capabilities within a tenant
- [Enterprise Edition](enterprise.md) — Activate your EE license
- [Authentication](../deployment/authentication.md) — Set up SSO providers for tenant mapping
- [Hub](hub.md) — Manage platform-wide resources shared across tenants
{% endif %}