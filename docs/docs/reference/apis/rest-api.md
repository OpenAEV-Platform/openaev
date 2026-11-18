# REST API

OpenAEV provides a REST API that lets you perform any platform action programmatically. Any action available in the graphical interface can also be run through the API.

## Authentication

Every `/api/` endpoint requires authentication, except a few public ones such as `/api/login`.

### API key

Each user has one API key. It carries all the permissions of that user.

1. Open the profile menu in the top right corner and select **Profile**.
2. In the **API access** section, copy the **Token key**.
3. Send the key in the `Authorization` header of every request:

```http
Content-Type: application/json
Authorization: Bearer <API_KEY>
```

API key requests are stateless: the server creates no session for them.

!!! warning

    Keep your API key secret. If it leaks, click **RENEW** in **Profile > API access**: the old key stops working at once.

    Do not send cookies along with a bearer request. Requests that carry both are checked for a CSRF (Cross-Site Request Forgery) token, like browser requests.

### Session cookie

The API also accepts the browser session cookie, with CSRF protection on `POST`, `PUT` and `DELETE`. For scripts, use an API key.

## API documentation (Swagger UI)

The API is described by an OpenAPI 3.1 specification and served through Swagger UI, where you can browse endpoints, inspect request and response schemas, and test calls from the browser after authenticating.

- **Swagger UI**: `https://<your-openaev-url>/swagger-ui/index.html`, also linked from **API specifications** in **Profile > API access**
- **OpenAPI schema (JSON)**: `https://<your-openaev-url>/api-docs`

## Common patterns

### Base URL

All API endpoints are prefixed with `/api/`. Tenant-scoped endpoints use the prefix `/api/tenants/{tenantId}/` instead (see [Multi-tenant context](#multi-tenant-context)).

The API uses JSON for request and response bodies. Include the `Content-Type: application/json` header in requests that carry a body.

### Pagination

Search endpoints take a `POST` request with the pagination input in the body:

```http
POST /api/scenarios/search
```

```json
{
  "page": 0,
  "size": 20,
  "textSearch": "ransomware",
  "sorts": [
    { "property": "scenario_name", "direction": "asc" }
  ],
  "filterGroup": {
    "mode": "and",
    "filters": []
  }
}
```

| Field | Type | Description |
|---|---|---|
| `page` | integer | Page number, zero-indexed. Default: `0` |
| `size` | integer | Number of elements per page. Default: `20`, maximum: `1000` |
| `textSearch` | string | Optional free-text search across searchable attributes |
| `sorts` | array | Sort fields, each with a `property` and an optional `direction` (`asc` by default, or `desc`) |
| `filterGroup` | object | Structured filter, see [Filtering](#filtering) |

The response wraps results in a page object:

```json
{
  "content": [ ... ],
  "totalElements": 142,
  "totalPages": 8,
  "number": 0,
  "size": 20
}
```

### Filtering

The `filterGroup` field combines filters with `and` or `or`. For the format and the list of operators, see [Filters](filters.md).

### Dashboard widget analytics

The dashboard widget endpoints (`/api/dashboards/count`, `average`, `series`, `entities`, `entities-runtime`, `attack-paths` and their `/api/dashboards/adhoc/*` variants) select the data with a `base_entity` filter in each series or list perspective. `GET /api/dashboards/base-entities` returns the valid values, for example `asset`, `finding` or `scenario`.

A `base_entity` value outside that list answers `400` with a `message` that names the value and lists the valid ones, instead of a count of `0`. A retired value says what replaced it: `endpoint` became `asset`.

A structural histogram (`widget_configuration_type` `structural-histogram`) groups by a `field` of the search document of its `base_entity`. `GET /api/dashboards/group-by-fields` returns the valid fields, keyed by `base_entity`. A field outside that list answers `400` instead of an empty series, with a `message` that names the field and lists the valid ones. Some filters of the findings list run on the database and are not indexed: the message names their indexed equivalent, `finding_assets` becomes `base_endpoint_side` and `finding_asset_groups` becomes `base_asset_groups_side`.

Findings can be grouped by asset group with `base_asset_groups_side`: the asset groups the injects of the finding's occurrences target. A finding found through several asset groups counts in each of them.

### Error responses

| Code | Meaning |
|---|---|
| `400` | Bad request -- validation failed or malformed input |
| `401` | Unauthorized -- missing or invalid authentication |
| `403` | Forbidden -- authenticated but insufficient permissions |
| `404` | Not found -- resource does not exist or is not accessible |
| `409` | Conflict -- database constraint violation or resource lock |
| `429` | Too many requests -- rate limit reached, when [rate limiting](#rate-limiting) is enabled |
| `500` | Internal server error |

Validation errors (`400`) return per-field error messages. Other errors return a JSON body with a `message` field.

## Multi-tenant context

The API exposes two sets of endpoints:

- **Platform endpoints** stay under `/api/...` and handle platform-wide operations (e.g., Tenant management, platform settings).
- **Tenant-scoped endpoints** use the prefix `/api/tenants/{tenantId}/...`, where `{tenantId}` is the UUID of the target Tenant.

Most resource endpoints (Scenarios, Simulations, Assets, Teams) support both patterns. Accessing a Tenant you are not authorized for returns `403`. See [Multi-tenancy](../../administration/multi-tenancy.md).

## Rate limiting

Rate limiting is a preview feature, off by default. It runs only when `RATE_LIMITING` is listed in `openaev.enabled-dev-features`. When enabled, the default limits are:

| Requests | Default limit | Counted per |
|---|---|---|
| Not authenticated | 10 requests per second | Originating IP address, all endpoints together |
| Authenticated | 300 requests per second | User account and endpoint |

The unauthenticated limit also covers `/login`, `/oauth2`, `/saml2`, `/actuator` and `/xtm`.

Tune the limits in the [server configuration](../deployment/configuration.md#rate-limiting).

## Examples

Create a Scenario:

```http
POST /api/scenarios
```

```json
{
  "scenario_name": "APT28 phishing campaign",
  "scenario_description": "Simulates a spear-phishing attack chain",
  "scenario_severity": "high"
}
```

Update it with the full resource data, or delete it:

```http
PUT /api/scenarios/{scenarioId}
DELETE /api/scenarios/{scenarioId}
```

To list Scenarios, see [Pagination](#pagination).

Import Injects from an XLS file into a Scenario. Each call needs the right to edit the Scenario, not the mapper:

```http
POST /api/tenants/{tenantId}/scenarios/{scenarioId}/xls/store
POST /api/tenants/{tenantId}/scenarios/{scenarioId}/xls/{importId}/dry
POST /api/tenants/{tenantId}/scenarios/{scenarioId}/xls/{importId}/import
```

1. `store`, available only under the Tenant prefix, uploads the file as `multipart/form-data` in a `file` field (`.xls` or `.xlsx`, under 25 MB). It returns the `import_id` and the `available_sheets`. The file is kept until a successful `import`, for at most 60 minutes.
2. `dry` previews the Injects without saving them. `import` creates them. Both take the same body:

```json
{
  "sheet_name": "Timeline",
  "import_mapper_id": "<mapper-uuid>",
  "timezone_offset": 0
}
```

For a Simulation, use the same calls under `/api/tenants/{tenantId}/exercises/{exerciseId}/xls/...`.

## Exploration graph

The exploration graph links Assets, Asset groups, Findings, Injects, Simulations and Scenarios. The API returns lazy slices of it, never the whole graph: one entity and its first ring, a path between two entities, or the risk-first landing view. Each endpoint is available under `/api/graph/...` and under the Tenant prefix `/api/tenants/{tenantId}/graph/...`.

```http
GET /api/graph/hot-paths?limit=15
GET /api/graph/{entityType}/{entityId}/neighbourhood?edges=FOUND_ON,TARGETS&limit=50
GET /api/graph/path?fromType=asset&fromId={assetId}&toType=scenario&toId={scenarioId}
```

| Endpoint | Returns | Parameters |
|---|---|---|
| `hot-paths` | The exposure hot paths: the entities with the most failed expectations and findings | `limit`, default `15` |
| `{entityType}/{entityId}/neighbourhood` | The entity and its direct neighbours | `edges`: comma-separated edge types to expand, all when empty; `limit`: neighbours per edge type, default `50`, maximum `200` |
| `path` | A shortest path between two entities, at most 6 hops; an empty path when none is found | `fromType`, `fromId`, `toType`, `toId` |

- **Entity types** (case-insensitive): `asset`, `asset_group`, `finding`, `inject`, `simulation`, `scenario`, `person`, `organization`, `team`. An unknown type returns `400`.
- **Edge types**: `MEMBER_OF` (Asset group contains Asset, static or through the group's dynamic filter), `FOUND_ON` (Finding observed on Asset), `PRODUCED` (Inject produced Finding), `TARGETS` (Inject targeted Asset or Asset group, with its expectation result), `PART_OF` (Inject belongs to Simulation), `RUN_OF` (Simulation is a run of Scenario), `IDENTITY_OF` (Asset is the identity of a Person), `WORKS_FOR` (Person belongs to Organization), `IN_TEAM` (Person is a member of Team), `TEAM_OF` (Team belongs to Organization), `LINKED_TO` (Asset linked to another Asset, see [Asset links](#asset-links)), `ATTACK_STEP` (an attack path step of a Simulation between two Assets). An unknown edge type returns `400`.
- **Access**: the endpoints need the right to read Assets. Simulation, Scenario and Inject nodes are returned only when you can read them in the Tenant of the request; Person, Organization and Team nodes only with the right to read players, organizations or teams; the others are dropped with their edges.

The response is one graph slice:

```json
{
  "graph_focus": "ASSET:<asset-uuid>",
  "graph_nodes": [
    {
      "node_id": "ASSET:<asset-uuid>",
      "node_type": "ASSET",
      "node_entity_id": "<asset-uuid>",
      "node_label": "srv-web-01",
      "node_subtype": "Endpoint",
      "node_result": "FAILED",
      "node_failed_count": 2,
      "node_finding_count": 3
    }
  ],
  "graph_edges": [
    {
      "edge_id": "TARGETS|INJECT:<inject-uuid>|ASSET:<asset-uuid>",
      "edge_type": "TARGETS",
      "edge_source": "INJECT:<inject-uuid>",
      "edge_target": "ASSET:<asset-uuid>",
      "edge_result": "FAILED"
    }
  ],
  "graph_truncated": false,
  "graph_path": []
}
```

A node id is `<TYPE>:<entity id>`. `node_result` and `edge_result` are `SUCCESS`, `PENDING` or `FAILED`, the worst expectation result behind them. `graph_truncated` is `true` when a limit cut neighbours: request the entity again with a higher `limit` to load more. `graph_path` lists the node ids of the path, in order, for a `path` request.

## Asset links

An Asset can be linked to other Assets of the same Tenant: an identity Asset logged on to an endpoint, a service running on a host, a host reaching another. The exploration graph draws each link as a `LINKED_TO` edge. Each endpoint is available under `/api/assets/{assetId}/links` and under the Tenant prefix `/api/tenants/{tenantId}/assets/{assetId}/links`.

```http
GET /api/assets/{assetId}/links
POST /api/assets/{assetId}/links
DELETE /api/assets/{assetId}/links/{linkedAssetId}/{linkType}
```

- **Link types**: `CONNECTS_TO`, `DEPENDS_ON`, `HOSTS`, `LOGGED_ON`, `RUNS_ON`, `USES`, read from the Asset of the path to the linked Asset.
- **Body** of `POST`: `{"linked_asset_id": "<asset-uuid>", "asset_link_type": "LOGGED_ON"}`. Adding a link that exists returns it unchanged.
- **Errors**: `400` for a link to the Asset itself or to an Asset of another Tenant, `404` when either Asset (or, on `DELETE`, the link) is not found.
- **Access**: `GET` needs the right to read the Asset, `POST` and `DELETE` the right to manage it. `GET` lists the links that start or end on the Asset; deleting an Asset deletes its links.

## What's next?

- [Filters](filters.md) -- Filter format and operators
- [Profile](../../administration/profile.md) -- Where to manage your account
