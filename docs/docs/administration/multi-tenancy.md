# Multi-tenancy

This page explains how to configure and manage multi-tenancy in OpenAEV, enabling multiple isolated workspaces on a single platform instance.

!!! tip "Enterprise Edition"

    Multi-tenancy is an **OpenAEV Enterprise Edition** feature. A valid EE license is required to create and manage tenants.
    See [Enterprise Edition](enterprise.md) for activation instructions.

## What is multi-tenancy?

Multi-tenancy allows a single OpenAEV instance to host multiple **isolated workspaces**, called Tenants. Each Tenant has its own data, users, roles, groups, integrations, and infrastructure, completely separated from other Tenants on the same platform.

## Why use multi-tenancy?

Multi-tenancy is the recommended deployment model for:

- **MSSPs** managing multiple customers from a single platform
- **Large organizations** isolating business units or subsidiaries

It reduces operational overhead by centralizing infrastructure while guaranteeing strict data isolation between Tenants.

## Concepts

### Tenant

A Tenant is a fully isolated workspace within the OpenAEV platform. It contains:

- Its own **users, groups, and roles**
- Its own **Scenarios, Simulations, and Atomic Tests**
- Its own **Assets, Teams, and Players**
- Its own **integrations** (Injectors, Collectors, Executors)

Data from one Tenant is never visible to users of another Tenant.

### Platform level vs. Tenant level

OpenAEV distinguishes two levels of administration:

- The **platform level** is where you manage Tenants, platform-wide users, roles, and groups. Platform administrators operate at this level to create Tenants, assign users across Tenants, and configure global settings.
- The **Tenant level** is where day-to-day work happens: Scenarios, Simulations, Assets, Findings, integrations. Each Tenant has its own users, groups, and roles that are independent from other Tenants.

A user or group can exist at both levels. For example, a platform administrator manages Tenants globally but must be explicitly added to a Tenant group to access that Tenant's data.

## Managing Tenants

Manage Tenants from **Settings > Security > Platform > Tenants**. You need the `Manage platform settings` capability (platform administrator).

From this page you can create, edit, and delete Tenants. When you create a Tenant, it is immediately active and all built-in integrations (Injectors, Collectors) are automatically registered for it.

### Soft-delete and reactivation

Tenant deletion is a **soft-delete** operation. The Tenant and all its data are retained for **30 days** before permanent purge. During this period, you can reactivate the Tenant from the same page.

!!! note "The default Tenant cannot be deleted"

    The platform's **default Tenant** cannot be deleted. In the Tenants list, its delete action is disabled.

!!! danger "Permanent deletion after 30 days"

    After 30 days, the Tenant and **all its data** (Scenarios, Simulations, Assets, Findings, documents) are permanently purged and cannot be recovered.

## Users and access in a multi-tenant setup

### Assigning users to a Tenant

You assign a user to a Tenant directly from the Tenant's user management. Once assigned, the user's permissions within that Tenant are determined by the groups and roles they belong to in that Tenant context.

A user can belong to **multiple Tenants** simultaneously. Permissions are evaluated independently in each Tenant context.

## SSO and Tenant mapping

When using SSO (Single Sign-On) with OpenID Connect or SAML2, you can automatically assign users to a specific Tenant at login using the `tenant_id` parameter.

### Configuration

Add the following property for your SSO provider registration:

| Parameter | Environment variable | Description |
|---|---|---|
| `openaev.provider.{registrationId}.tenant_id` | `OPENAEV_PROVIDER_{registrationId}_TENANT_ID` | Tenant ID to assign users to when they log in via this SSO provider |

### Example

Map an Azure AD SAML2 provider to a specific Tenant:

```properties
OPENAEV_PROVIDER_AZURE_TENANT_ID=<your-tenant-uuid>
```

## What's next?

- [Users and RBAC](users-and-rbac.md) -- Configure roles and capabilities within a Tenant
- [Enterprise Edition](enterprise.md) -- Activate your EE license
- [Authentication](../deployment/authentication.md) -- Set up SSO providers for Tenant mapping
- [Hub](hub.md) -- Manage platform-wide resources shared across Tenants
