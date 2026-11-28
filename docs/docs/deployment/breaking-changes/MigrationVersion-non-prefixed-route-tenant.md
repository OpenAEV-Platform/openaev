# Tenant of the non-prefixed API routes

!!! info ""

    * **Introduced in**: `OpenAEV [MigrationVersion]`

## Description of changes

The non-prefixed API routes (`/api/...`, without `/api/tenants/{tenantId}`) read the tenants of the request scope: the tenants named in the `X-Tenant-Ids` header, or every tenant of the caller when the header is absent. The endpoints below read the default tenant on these routes, whatever the header said. They now follow the request scope:

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
| `GET /api/injects/expectations` and its polls `/{sourceId}`, `/assets/{sourceId}`, `/prevention`, `/prevention/{sourceId}`, `/detection`, `/detection/{sourceId}`, `/ai/{sourceId}` | expectations of B; `400` when the header names several tenants | expectations of the single tenant of the caller; for a caller of several tenants, the default tenant when the caller belongs to it, `400` otherwise |
| `GET /api/injects/expectations/logging/{sourceId}` | an empty list while the Logging expectation feature is disabled, whatever the tenants; with the feature enabled, the same as the other polls | same |
| `GET /api/endpoints/{endpointId}`, `PUT /api/endpoints/{endpointId}` | endpoint of B, or of any tenant the header names; tags and linked user checked in the tenant of the endpoint | endpoint of the single tenant of the caller; for a caller of several tenants, the default tenant when the caller belongs to it, `400` otherwise |
| `GET /api/agent/installer/openaev/token`, `GET /api/agent/installer/openaev/{platform}/{installationMode}` | token and install command of B; `400` when the header names several tenants | token and install command of the single tenant of the caller; for a caller of several tenants, the default tenant when the caller belongs to it, `400` otherwise |

A bulk operation launched over several tenants carries no tenant: its launcher sees it on every route and every stream, and no other user sees it.

On the non-prefixed routes, these image responses carry `Cache-Control: no-store`: the same URL serves the image of the tenant the caller selects, so a browser or a shared cache must not store it. On the tenant-prefixed routes, which name the tenant in the URL, the connector images are cacheable for five minutes (`Cache-Control: max-age=300`).

The tenant-prefixed routes (`/api/tenants/{tenantId}/...`), which the web interface uses, are unchanged.

## Impact

API clients that call these endpoints on the non-prefixed route with an `X-Tenant-Ids` header naming a tenant other than the default one read and write that tenant. A client in several tenants that sets an organization through `PUT /api/me/profile` without the header receives `400`.

The collectors, the EDR integrations and the agent install scripts call the expectation polls, the endpoint read and update and the agent installer on the non-prefixed route, mostly without the header:

* A caller that belongs to one tenant, the default one, receives the same expectations, endpoint, token and install command as before. This covers every single-tenant deployment.
* A caller that belongs to several tenants, the default one among them, receives those of the default tenant, read with the rights it holds there.
* A caller that belongs to several tenants but not to the default one receives `400`, and an empty list on the logging poll while the Logging expectation feature is disabled.
* A caller that belongs to one tenant other than the default one and passes the access check on the non-prefixed route (an administrator) receives those of its own tenant, where it received those of the default tenant: an empty list of expectations, a `404` on the endpoint, the default tenant's token and install command.
* A request whose header names a tenant the caller does not belong to receives `403`. A request whose header names several tenants receives `400` on the expectation polls and the agent installer.

On the non-prefixed route, the access check of these endpoints reads the rights the caller holds in the default tenant: a caller with no rights there receives `403`, as before this version, and calls the prefixed route of its tenant.

## Migration guide

Name the target tenant, either with the prefixed route `/api/tenants/{tenantId}/...` or with `X-Tenant-Ids: {tenantId}`, on every call that writes a tenant-scoped value.

Configure a collector, an EDR integration or an agent install script of a tenant other than the default one, or of several tenants, with the prefixed route of its tenant, for example `/api/tenants/{tenantId}/injects/expectations/prevention/{sourceId}` and `/api/tenants/{tenantId}/agent/installer/openaev/token`, or with `X-Tenant-Ids` naming that single tenant. An install script that sends `Accept: text/plain` receives the status and its code as plain text.

To read the connector image of a tenant, call the prefixed route, for example `/api/tenants/{tenantId}/collectors/{type}/image`, or send `X-Tenant-Ids` with that single tenant. Clients that cache images rely on the prefixed routes, since the non-prefixed image responses are not stored.

## Validation checklist after upgrade

1. With `X-Tenant-Ids: {tenantId}`, read a finding of that tenant through `GET /api/findings/{id}` and confirm it answers `200`.
2. Update a profile organization with the header and confirm the organization shows in that tenant.
3. Request `GET /api/tenants/{tenantId}/collectors/{type}/image` and confirm it answers `200` with `Cache-Control: max-age=300`, then the same image on `GET /api/collectors/{type}/image` with `X-Tenant-Ids: {tenantId}` and confirm `Cache-Control: no-store`.
4. As a collector of a tenant, poll `GET /api/injects/expectations` with `X-Tenant-Ids: {tenantId}` and without the header, and confirm each answer lists the expectations of that tenant.
5. Download the agent installer token with `GET /api/agent/installer/openaev/token`, with and without `X-Tenant-Ids: {tenantId}`, and confirm it is the token shown in the agent install dialog of that tenant.
