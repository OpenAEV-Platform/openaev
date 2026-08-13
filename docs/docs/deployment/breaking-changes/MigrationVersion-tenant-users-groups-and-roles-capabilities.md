# Tenant users, groups and roles capabilities

!!! info ""

    * **Introduced in**: `OpenAEV [MigrationVersion]`

## Description of changes

Managing a tenant's users, groups and roles used to come with the *tenant settings* capabilities, which also cover unrelated administration surfaces: collectors, injectors, tag rules, attack patterns, organizations, tags. Granting someone the right to create a user therefore also granted them the right to deploy a connector.

Three capabilities now govern that surface on their own, mirroring the platform-side triad:

| Capability | Grants |
|:-----------|:-------|
| `Access tenant users, groups and roles` | Read and search tenant users, groups and roles. |
| `Manage tenant users, groups and roles` | Create and update them. Requires *Access tenant users, groups and roles*. |
| `Delete tenant users, groups and roles` | Delete them. Requires *Manage tenant users, groups and roles*. |

The `USER`, `USER_GROUP` and `GROUP_ROLE` resources move out of the tenant settings capabilities, which keep everything else unchanged.

## Impact

- **Existing roles**: migrated automatically, see below. No role gains or loses access on upgrade.
- **New roles**: the two sets are now independent. A role granted only *Manage tenant settings* can no longer create users, and a role granted only *Manage tenant users, groups and roles* can no longer deploy connectors. Grant both where the old behaviour is wanted.
- **API clients**: calls to the tenant user, group and role endpoints are enforced against the new capabilities. A token whose role holds only the tenant settings capabilities receives `403` on those endpoints after upgrade unless the migration granted it the new ones.

## Migration guide

Upgrade OpenAEV. A Flyway migration grants every tenant role the new capability matching the tenant settings tier it already holds:

| Already held | Granted |
|:-------------|:--------|
| `Access tenant settings` | `Access tenant users, groups and roles` |
| `Manage tenant settings` | `Access` + `Manage tenant users, groups and roles` |
| `Delete tenant settings` | the three new capabilities |

The migration only touches tenant-scoped roles, never platform roles, and is idempotent. No manual action is required.

!!! warning

    The grants are applied directly in database, outside the role-edit API, so they do not appear in the audit log. The capability set of each role after upgrade is visible in **Settings > Roles**.

## Validation checklist after upgrade

1. Open **Settings > Roles** and confirm each role that could manage users now also lists the matching *tenant users, groups and roles* capability.
2. Sign in as a user whose role held *Manage tenant settings* and confirm creating a tenant user still works.
3. Create a role holding only *Manage tenant settings* and confirm it can no longer create a tenant user — this is the intended new behaviour.
