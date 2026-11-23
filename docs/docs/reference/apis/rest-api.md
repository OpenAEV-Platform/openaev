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

Read what uses a custom Dashboard before deleting it:

```http
GET /api/custom-dashboards/{customDashboardId}/usage
```

```json
{
  "custom_dashboard_id": "<dashboard-uuid>",
  "scenarios_count": 2,
  "simulations_count": 1,
  "default_home_dashboard": false,
  "default_scenario_dashboard": false,
  "default_simulation_dashboard": true
}
```

`DELETE /api/custom-dashboards/{customDashboardId}` is refused only for the default home Dashboard. Otherwise it succeeds, clears the Dashboard from the Scenarios and Simulations that use it and empties the default settings that name it, so call `usage` first to warn the user.

Re-create the starter pack Dashboards that were deleted:

```http
POST /api/custom-dashboards/starter-pack/restore
```

It imports each bundled starter pack Dashboard whose name no longer exists, under its original name, and sets the default scenario and simulation Dashboards back when they are empty. A second call creates nothing. The answer lists `restored_dashboards`, `existing_dashboards` and `defaults_set`.

## Exploration graph

The exploration graph links Assets, Asset groups, Findings, Injects, Simulations and Scenarios. The API returns lazy slices of it, never the whole graph: one entity and its first ring, a path between two entities, or the risk-first landing view. Each endpoint is available under `/api/graph/...` and under the Tenant prefix `/api/tenants/{tenantId}/graph/...`.

```http
GET /api/graph/story
GET /api/graph/overview
GET /api/graph/starting-points
GET /api/graph/hot-paths?limit=15
GET /api/graph/{entityType}/{entityId}/neighbourhood?edges=FOUND_ON,TARGETS&limit=50
GET /api/graph/path?fromType=asset&fromId={assetId}&toType=scenario&toId={scenarioId}
```

| Endpoint | Returns | Parameters |
|---|---|---|
| `story` | The top exposure story of the landing page: one connected slice of about 30 nodes, the attack path with the most failed steps you can read, else the most exposed Asset with its failed Injects, Findings, Asset groups and identity; empty when nothing failed | none |
| `overview` | The aggregated estate: up to 40 Asset groups with their member count, scored and failed expectations and failure rate, a bucket for the Assets in no group, and the Simulations that targeted each group (at most 3 per group). Dynamic groups are flagged and counted by their static members | none |
| `starting-points` | The starting points of the landing page, at most 5 per section: exposed Assets, failing Asset groups, the latest Simulation, Findings by Assets affected, attack paths, exposed Persons | none |
| `hot-paths` | The exposure hot paths: the entities with the most failed expectations and findings (the former landing view) | `limit`, default `15` |
| `{entityType}/{entityId}/neighbourhood` | The entity and its direct neighbours | `edges`: comma-separated edge types to expand, all when empty; `limit`: neighbours per edge type, default `50`, maximum `200` |
| `path` | A shortest path between two entities, at most 6 hops; an empty path when none is found | `fromType`, `fromId`, `toType`, `toId` |

- **Entity types** (case-insensitive): `asset`, `asset_group`, `finding`, `inject`, `simulation`, `scenario`, `person`, `organization`, `team`. An unknown type returns `400`.
- **Edge types**: `MEMBER_OF` (Asset group contains Asset, static or through the group's dynamic filter), `FOUND_ON` (Finding observed on Asset), `PRODUCED` (Inject produced Finding), `TARGETS` (Inject targeted Asset or Asset group, with its expectation result), `PART_OF` (Inject belongs to Simulation), `RUN_OF` (Simulation is a run of Scenario), `IDENTITY_OF` (Asset is the identity of a Person), `WORKS_FOR` (Person belongs to Organization), `IN_TEAM` (Person is a member of Team), `TEAM_OF` (Team belongs to Organization), `LINKED_TO` (Asset linked to another Asset, see [Asset links](#asset-links)), `ATTACK_STEP` (an attack path step of a Simulation between two Assets). An unknown edge type returns `400`.
- **Freshness**: `story`, `overview` and `starting-points` read their tenant-wide rankings at most once a minute per Tenant scope; your access is applied on every request.
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
- **Origin**: each link carries `asset_link_origin` (`MANUAL`, `AGENT`, `COLLECTOR`, `SYNC`) and `asset_link_last_seen_at`, the last time an automatic source reported it. When an OpenAEV agent registers, the account it runs as (`agent_executed_by_user`, also matched without its `DOMAIN\` or `@domain` part) is looked up among the IDENTITY Assets of the endpoint's Tenant: a single match gets a `LOGGED_ON` link to the endpoint with origin `AGENT`, refreshed at each registration. System accounts (`root`, `NT AUTHORITY\SYSTEM`) are never linked. A person always wins: adding by hand a link an agent reported makes it `MANUAL`, an agent never changes a manual link, and deleting an automatic link keeps it hidden so the next registration does not restore it (adding it by hand brings it back).

## Asset owners

A person or a Team owns an Asset as business owner (`OWNED_BY`) or technical owner (`MANAGED_BY`); an Asset can have several owners. Part of the exploration graph preview feature like the Asset links (`404` while `EXPLORATION_GRAPH` is off); the graph draws `OWNED_BY` and `MANAGED_BY` edges from the Asset to the person or Team. Each endpoint is also available under the Tenant prefix.

```http
GET /api/assets/{assetId}/owners
POST /api/assets/{assetId}/owners
DELETE /api/assets/{assetId}/owners/{ownerId}
GET /api/users/{userId}/owned-assets
GET /api/teams/{teamId}/owned-assets
```

- **Body** of `POST`: `{"asset_owner_user_id": "<user-uuid>", "asset_owner_role": "OWNED_BY"}` or `{"asset_owner_team_id": "<team-uuid>", "asset_owner_role": "MANAGED_BY"}`, with an optional `asset_owner_source` (`MANUAL` by default, `COLLECTOR` or `SYNC` for an automated writer). Adding an owner that exists returns it unchanged.
- **Output**: `asset_owner_id`, `asset_id`, `asset_name`, `asset_owner_kind` (`USER` or `TEAM`), `asset_owner_user_id` or `asset_owner_team_id`, `asset_owner_name`, `asset_owner_role`, `asset_owner_source`, `asset_owner_created_at`.
- **Errors**: `400` when the body names neither or both of a person and a Team, a person who is not a member of the Asset's Tenant, or a Team of another Tenant; `404` when the Asset, the Team or (on `DELETE`) the owner is not found.
- **Access and scope**: the same as the Asset links; an owner follows the markings of its Asset. Deleting the Asset, the person or the Team deletes the owner.

## Asset bulk update

Updates the classification of many Assets in one call, over the same selection as the bulk delete: explicit ids, or the Asset search input with exclusions. Security platforms are never selected. Available under `/api/assets/bulk` and under the Tenant prefix `/api/tenants/{tenantId}/assets/bulk`.

```
PUT /api/assets/bulk
```

- **Selection**: `asset_ids_to_process`, or `search_pagination_input` with `asset_ids_to_ignore`.
- **Body**: `asset_category`, `asset_subcategory`, `asset_url`, `asset_criticality`, `asset_tags_to_add`. A field left out is unchanged; tags are added to the existing ones, never replaced. A category change with no subcategory clears a subcategory that no longer belongs to the new category.
- **Errors**: `400` when nothing is to be updated, for a subcategory without a category, or for a subcategory outside its category; `404` when a tag id is unknown in the Tenant. Nothing is written on an error.
- **Access**: the right to manage Assets.
- **Example**, reclassify every Asset carrying a tag:

```json
{"search_pagination_input": {"filterGroup": {"mode": "and", "filters": [{"key": "asset_tags", "operator": "contains", "values": ["<tag id>"], "mode": "or"}]}},
 "asset_category": "WEB_APPLICATION", "asset_subcategory": "WEBSITE"}
```

The subcategory is checked against its category on Asset creation and update too (`400`); an Agent, collector or injector registration drops a mismatched subcategory instead of failing.

## Asset freshness

Every Asset carries when it was last observed and by what, and turns stale once it is unseen for longer than the Tenant allows.

```http
GET /api/tenants/{tenantId}/tenant-settings/asset-freshness
PUT /api/tenants/{tenantId}/tenant-settings/asset-freshness
```

- **Asset fields**: `asset_last_seen` (the latest observation) and `asset_last_seen_source` (what observed it, e.g. `agent:openaev_agent`), filterable and sortable in the Asset searches. Staleness is not a field of its own: `asset_status` reads `STALE` when the Asset was last seen longer ago than the Tenant allows, and its usual value (`ACTIVE`, `INACTIVE`, `AGENTLESS`) otherwise. An Asset never observed has no `asset_last_seen` and is never `STALE`.
- **Observations**: an Agent that registers or sends a heartbeat stamps its Asset, at most once an hour per source so a heartbeat does not rewrite the Asset every minute. A `STALE` Asset that is observed again leaves `STALE` at once.
- **Settings body**: `{"asset_stale_after_days": 30}`, from 0 to 3650. `0` by default: the check is off, no Asset turns stale and no event is emitted until a user sets a number of days. The last seen stamp is recorded either way.
- **Discovery marker**: `{"asset_discovery_tag": true}` on the same endpoint tags with `discovered` every Endpoint a run creates (an autonomous run promoting a finding, an inject whose output brings a new asset), so a filter or a live trigger on Asset creation tells it from a declared one. `false` by default: no tag is created or added. Omitted on an update, the current value is kept.
- **Job**: every hour (`openaev.cron.config.asset.freshness.interval`, in minutes), each Asset that turned `STALE` since the previous pass gets one Asset update event, so a live trigger on Asset updates receives each transition once. Nothing is written on the Asset.

## Reachability assertions

A Scenario can be declared a reachability assertion: a recurring check, from where its Injects run, that its targets still answer as expected (a port closed, an address answering 403, a protocol off). The expected answer is the Expectation of each Inject; the check runs on the Scenario's recurrence.

```http
PUT /api/scenarios/{scenarioId}/reachability-assertion
PUT /api/tenants/{tenantId}/scenarios/{scenarioId}/reachability-assertion
```

- **Body**: `{"scenario_reachability_assertion": true}` declares it, `false` clears it. `scenario_reachability_assertion` is `false` on every Scenario by default and filterable in the Scenario searches; a Scenario that is not an assertion behaves exactly as before.
- **Read-only only**: every enabled Inject of an assertion must be read-only (`injector_contract_read_only` on its Injector contract, or `payload_read_only` on its Payload). Declaring a scheduled Scenario, or scheduling an assertion with `PUT /api/scenarios/{scenarioId}/recurrence`, is refused with `400` naming the Injects that are not read-only, and the scheduler skips an assertion that holds one (an Inject made writable after scheduling never runs on its own).
- **Access**: the launch right on the Scenario, as for its recurrence.
- **Results**: a failed check is reported like any simulation: the Inject's output parser records its findings (an open port, an answer) on the target, and when a run scores lower than the previous one the existing `SCORE_DEGRADATION` notification event fires.

## Coverage scenario read-only first

Scenarios built from an OpenCTI Security Coverage can be built with only their read-only Injects enabled. See [Scenario generation from OpenCTI Security Coverage](../../usage/build/scenario/security-coverage.md#read-only-first-build-and-run-order).

```http
GET /api/tenants/{tenantId}/tenant-settings/coverage-scenarios
PUT /api/tenants/{tenantId}/tenant-settings/coverage-scenarios
```

- **Settings body**: `{"coverage_scenario_read_only_first": false}`, `false` by default. With the default, a Security Coverage builds and schedules its Scenario exactly as before.
- **Access**: `GET` needs the right to read Tenant settings, `PUT` the right to manage them.
- **Read-only marker**: `injector_contract_read_only` on Injector contracts and `payload_read_only` on Payloads, both `false` by default.
- **Starting a held Scenario**: the existing `PUT /api/scenarios/{scenarioId}/recurrence` (launch right on the Scenario, records `scenario_scheduled_by`) and `inject_enabled` on each held Inject. Run order: the existing `inject_depends_duration` of each Inject.

## Finding re-test

A Finding carries a lifecycle status, returned by `GET /api/findings/{id}`:

- `finding_status`: `OPEN` (detected, not disproved by a later re-test), `NOT_REPRODUCED` (a re-test ran on the same targets and did not detect it again) or `RESOLVED`. Every new detection sets it back to `OPEN`.
- `finding_last_tested`: when a re-test last ended on the Finding.
- `finding_status_inject_id`: the Inject whose run set the status, a detection or a re-test.
- `finding_retest_inject_id`: the re-test Inject queued and not finished yet, else empty.

To re-test Findings, send their ids. The endpoint is also available under the Tenant prefix `/api/tenants/{tenantId}/findings/retest`.

```http
POST /api/findings/retest
```

```json
{"finding_ids": ["<finding-uuid>", "<finding-uuid>"]}
```

- **What runs**: for each Inject that last produced the Findings, one copy with the same contract, content and targets; the original Inject and its history are kept. The copy runs under the same contract as the original, so a test limited to read-only contracts stays so. No approval is asked.
- **Response**: one entry per Finding, `{"finding_id": "...", "retest_inject_id": "..."}`. Follow the Inject to know when the re-test ends.
- **Result**: when the copy finishes, a Finding it detected again stays `OPEN`; a Finding it did not detect turns `NOT_REPRODUCED`, with the copy as `finding_status_inject_id`. A copy that ends in error leaves the status unchanged. Each change emits a Finding update event.
- **Errors**: `400` when an Asset of a Finding is no longer a target of its Inject, directly or through an Asset group; `404` when a Finding is not in the Tenant or has no Inject left.
- **Access**: the right to launch each Inject that is copied.

## Marking definitions in OpenCTI format

Every Marking definition carries `marking_definition_stix_id`, the STIX id OpenCTI gives the same marking: OpenCTI's fixed ids for TLP:CLEAR, TLP:GREEN, TLP:AMBER, TLP:AMBER+STRICT and TLP:RED, and for any other marking the id OpenCTI derives from its type and definition. A marking is then the same object in OpenAEV and in OpenCTI. The id is set when the Marking definition is created; it stays empty when another Marking definition of the Tenant already carries it (TLP:WHITE next to TLP:CLEAR).

To export the Marking definitions you are cleared for as a STIX 2.1 bundle of `marking-definition` objects, with OpenCTI's order and color:

```http
GET /api/tenants/{tenantId}/marking_definitions/stix
```

To import Marking definitions from a STIX 2.1 bundle (or a list of objects), keeping the id OpenCTI gave them:

```http
POST /api/tenants/{tenantId}/marking_definitions/stix
```

- **Response**: one entry per `marking-definition` object, `{"stixId": "...", "type": "...", "definition": "...", "outcome": "..."}`, with `CREATED`, `EXISTING` (the Tenant already has this STIX id), `CONFLICT` (a Marking definition of the same type and definition exists under another id) or `INVALID`. Other object types are ignored and nothing is overwritten.
- **Access**: the right to manage Marking definitions; a Marking definition above your own clearance on its type is refused, as when created by hand.

## What's next?

- [Filters](filters.md) -- Filter format and operators
- [Profile](../../administration/profile.md) -- Where to manage your account
