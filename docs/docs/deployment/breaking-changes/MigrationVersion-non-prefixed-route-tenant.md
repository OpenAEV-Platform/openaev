# Tenant of the non-prefixed API routes

!!! info ""

    * **Introduced in**: `OpenAEV [MigrationVersion]`

## Description of changes

The non-prefixed API routes (`/api/...`, without `/api/tenants/{tenantId}`) read the tenants of the request scope: the tenants named in the `X-Tenant-Ids` header, or every tenant of the caller when the header is absent. Some endpoints on these routes read the default tenant whatever the header said. They now follow the request scope:

| Endpoint | With `X-Tenant-Ids: B` | Without the header |
|:--|:--|:--|
| `GET /api/findings/{id}`, `GET /api/findings/{id}/summary` | finding of B; summary counted in the tenant of the finding | finding of any tenant of the caller |
| `GET /api/sessions`, `GET /api/sessions/{userId}`, `DELETE /api/sessions/...` | users of B | users of every tenant of the caller |
| `PUT /api/me/profile` with an organization | organization of B, also the `user_organization` of the response; `400` when the header names several tenants | the single tenant of the caller; `400` for a caller of several tenants |
| `PUT /api/me/profile` without an organization | clears the organization of B; clears none when the header names several tenants | clears the organization of the caller's tenant when the caller has one tenant |
| `POST /api/mappers/store/{importId}` (dry run) | mapper built in B; `400` when the header names several tenants | the single tenant of the caller; for a caller of several tenants, the default tenant when the caller belongs to it, `400` otherwise |
| Tenant role create, update and delete | reserved role ids checked in the role's tenant | same |
| `GET /api/bulk-operations` and the bulk operation stream events | operations of B, plus the caller's operations that carry no tenant | operations of every tenant of the caller, plus the caller's operations that carry no tenant |

A bulk operation launched over several tenants carries no tenant: its launcher sees it on every route and every stream, and no other user sees it.

The tenant-prefixed routes (`/api/tenants/{tenantId}/...`), which the web interface uses, are unchanged.

## Impact

API clients that call these endpoints on the non-prefixed route with an `X-Tenant-Ids` header naming a tenant other than the default one read and write that tenant. A client in several tenants that sets an organization through `PUT /api/me/profile` without the header receives `400`.

## Migration guide

Name the target tenant, either with the prefixed route `/api/tenants/{tenantId}/...` or with `X-Tenant-Ids: {tenantId}`, on every call that writes a tenant-scoped value.

## Validation checklist after upgrade

1. With `X-Tenant-Ids: {tenantId}`, read a finding of that tenant through `GET /api/findings/{id}` and confirm it answers `200`.
2. Update a profile organization with the header and confirm the organization shows in that tenant.
