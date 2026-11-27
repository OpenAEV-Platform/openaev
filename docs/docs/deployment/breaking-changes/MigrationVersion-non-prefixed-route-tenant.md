# Tenant of the non-prefixed API routes

!!! info ""

    * **Introduced in**: `OpenAEV [MigrationVersion]`

## Description of changes

The non-prefixed API routes (`/api/...`, without `/api/tenants/{tenantId}`) read the tenants of the request scope: the tenants named in the `X-Tenant-Ids` header, or every tenant of the caller when the header is absent. Some endpoints on these routes read the default tenant whatever the header said. They now follow the request scope:

| Endpoint | With `X-Tenant-Ids: B` | Without the header |
|:--|:--|:--|
| `GET /api/findings/{id}`, `GET /api/findings/{id}/summary` | finding of B; summary counted in the tenant of the finding | finding of any tenant of the caller |
| `GET /api/sessions`, `GET /api/sessions/user/{userId}`, `DELETE /api/sessions/{sessionId}`, `DELETE /api/sessions/user/{userId}` | users of B | users of every tenant of the caller |
| `PUT /api/me/profile` with an organization | organization of B, also the `user_organization` of the response; `400` when the header names several tenants | the single tenant of the caller; `400` for a caller of several tenants |
| `PUT /api/me/profile` without an organization | clears the organization of B; clears none when the header names several tenants | clears the organization of the caller's tenant when the caller has one tenant |
| `POST /api/mappers/store/{importId}` (dry run) | mapper built in B; `400` when the header names several tenants | the single tenant of the caller; for a caller of several tenants, the default tenant when the caller belongs to it, `400` otherwise |
| Tenant role create, update and delete | reserved role ids checked in the role's tenant | same |
| `GET /api/bulk-operations` and the bulk operation stream events | operations of B, plus the caller's operations that carry no tenant | operations of every tenant of the caller, plus the caller's operations that carry no tenant |
| `GET /api/collectors/{type}/image`, `GET /api/collectors/id/{collectorId}/image`, `GET /api/injectors/{type}/image`, `GET /api/images/executors/icons/{type}`, `GET /api/images/executors/banners/{type}` | image of B, or the default tenant's image of that type when B has none; `400` when the header names several tenants | image of the caller's single tenant; for a caller of several tenants, the default tenant when the caller belongs to it, `400` otherwise |
| `GET /api/images/security_platforms/id/{id}/{theme}`, `GET /api/images/channels/id/{id}/{theme}` for a security platform or channel without a logo | fake detector icon of the tenant of the security platform or channel | same |

A bulk operation launched over several tenants carries no tenant: its launcher sees it on every route and every stream, and no other user sees it.

On the non-prefixed routes, these image responses carry `Cache-Control: no-store`: the same URL serves the image of the tenant the caller selects, so a browser or a shared cache must not store it. On the tenant-prefixed routes, which name the tenant in the URL, the connector images are cacheable for five minutes (`Cache-Control: max-age=300`).

The tenant-prefixed routes (`/api/tenants/{tenantId}/...`), which the web interface uses, are unchanged.

## Impact

API clients that call these endpoints on the non-prefixed route with an `X-Tenant-Ids` header naming a tenant other than the default one read and write that tenant. A client in several tenants that sets an organization through `PUT /api/me/profile` without the header receives `400`.

## Migration guide

Name the target tenant, either with the prefixed route `/api/tenants/{tenantId}/...` or with `X-Tenant-Ids: {tenantId}`, on every call that writes a tenant-scoped value.

To read the connector image of a tenant, call the prefixed route, for example `/api/tenants/{tenantId}/collectors/{type}/image`, or send `X-Tenant-Ids` with that single tenant. Clients that cache images rely on the prefixed routes, since the non-prefixed image responses are not stored.

## Validation checklist after upgrade

1. With `X-Tenant-Ids: {tenantId}`, read a finding of that tenant through `GET /api/findings/{id}` and confirm it answers `200`.
2. Update a profile organization with the header and confirm the organization shows in that tenant.
3. Request `GET /api/tenants/{tenantId}/collectors/{type}/image` and confirm it answers `200` with `Cache-Control: max-age=300`, then the same image on `GET /api/collectors/{type}/image` with `X-Tenant-Ids: {tenantId}` and confirm `Cache-Control: no-store`.
