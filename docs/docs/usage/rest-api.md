# REST API

OpenAEV provides a REST API that lets you perform any platform action programmatically. You can automate workflows, integrate with external tools, and customize behavior. Any action available through the graphical interface can also be executed via the API.

## Why use the REST API?

The REST API is useful when you need to:

- **Automate repetitive tasks** such as creating Scenarios, launching Simulations, or importing Assets in bulk.
- **Integrate OpenAEV with external tools** like SIEMs (Security Information and Event Management systems), ticketing systems, or CI/CD (Continuous Integration/Continuous Deployment) pipelines.
- **Build custom Dashboards or reports** by querying platform data programmatically.

## Authentication

Accessing the OpenAEV API requires authentication. Two methods are supported.

### API key

An API key provides stateless, token-based access. The key inherits the permissions of the user who created it, so any action the user can perform through the interface can also be performed with the key.

1. Generate an API key from your user profile, or have an administrator create one on your behalf.
2. Include the key in the `Authorization` header of every request:

```http
Content-Type: application/json
Authorization: Bearer <API_KEY>
```

### Session cookie

Session-based authentication is useful for scripts that simulate browser interactions.

1. Send a `POST` request to `/api/login` with your credentials in the request body.
2. Extract the `JSESSIONID` cookie from the response.
3. Include the cookie in subsequent requests. The session remains valid as long as the cookie is active.

## API documentation (Swagger UI)

The API is documented using an OpenAPI 3.1 specification and served through Swagger UI. Swagger UI provides an interactive interface for exploring endpoints, inspecting request/response schemas, and testing calls directly from the browser.

- **Swagger UI**: `https://<your-openaev-url>/swagger-ui/index.html`
- **OpenAPI schema (JSON)**: `https://<your-openaev-url>/api-docs`

Endpoints are grouped by controller (e.g., Scenarios, Simulations, Assets, Teams). Each endpoint shows the expected request body, query parameters, and response model. You can authenticate directly in Swagger UI to execute requests against your instance.

## Common patterns

### Base URL

All API endpoints are prefixed with `/api/`. In a multi-tenant setup, Tenant-scoped endpoints use the prefix `/api/tenants/{tenantId}/` instead (see [Multi-tenant context](#multi-tenant-context) below).

### Request and response format

The API uses JSON for both request and response bodies. Always include the `Content-Type: application/json` header in requests that carry a body.

### Pagination

Search endpoints use `POST` requests with a structured pagination input in the request body. The general pattern is:

```http
POST /api/{resource}/search
```

Request body example -- search Scenarios sorted by name:

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
| `sorts` | array | List of sort specifications, each with `property` (field name) and `direction` (`asc` or `desc`) |
| `filterGroup` | object | Structured filter (see Filtering below) |

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

Filters allow fine-grained queries on search endpoints. A `filterGroup` combines individual filters with a boolean operator (`and` or `or`).

Example -- filter Scenarios by tag and severity:

```json
{
  "filterGroup": {
    "mode": "and",
    "filters": [
      {
        "key": "scenario_tags",
        "mode": "or",
        "operator": "eq",
        "values": ["ransomware", "phishing"]
      },
      {
        "key": "scenario_severity",
        "operator": "eq",
        "values": ["critical"]
      }
    ]
  }
}
```

Available operators: `eq`, `not_eq`, `contains`, `not_contains`, `starts_with`, `not_starts_with`, `empty`, `not_empty`, `gt`, `gte`, `lt`, `lte`.

For the complete filter format reference, see [Filters](../reference/apis/filters.md).

### Error responses

The API uses standard HTTP status codes:

| Code | Meaning |
|---|---|
| `400` | Bad request -- validation failed or malformed input |
| `401` | Unauthorized -- missing or invalid authentication |
| `403` | Forbidden -- authenticated but insufficient permissions |
| `404` | Not found -- resource does not exist or is not accessible |
| `409` | Conflict -- database constraint violation or resource lock |
| `500` | Internal server error |

Validation errors (400) return a structured body with per-field error messages. Other errors return a JSON body with a `message` field describing the issue.

## Grant-scoped endpoints

Global inventory endpoints such as `/api/endpoints`, `/api/asset_groups` or `/api/injector_contracts` require the corresponding platform capability (Assets, Threat Arsenal). A user who only holds a grant on a specific Simulation or Scenario can instead use resource-scoped counterparts that expose only the elements referenced by that resource. The chaining Workflow API follows this pattern:

| Endpoint | Description |
|---|---|
| `GET /api/workflows/{workflowId}/scope/endpoints` | Endpoints referenced by the workflow scope rules (allowlist and denylist) |
| `POST /api/workflows/{workflowId}/scope/endpoints/find` | Same as above, restricted to the requested IDs |
| `GET /api/workflows/{workflowId}/scope/asset-groups` | Asset groups referenced by the workflow scope rules |
| `POST /api/workflows/{workflowId}/scope/asset-groups/find` | Same as above, restricted to the requested IDs |
| `GET /api/workflows/{workflowId}/injector_contracts/{injectorContractId}` | An injector contract, returned only if one of the workflow steps references it |

These endpoints check the caller's grant on the workflow's parent Simulation or Scenario instead of the global capabilities, so the chaining scope and logic screens work for grant-only users. IDs that are not referenced by the workflow are silently ignored, which prevents using these endpoints to read arbitrary platform data.

## Multi-tenant context

When multi-tenancy is enabled, the API exposes two sets of endpoints:

- **Platform endpoints** remain under `/api/...` and handle platform-wide operations (e.g., Tenant management, platform settings).
- **Tenant-scoped endpoints** are prefixed with `/api/tenants/{tenantId}/...` where `{tenantId}` is the UUID of the target Tenant.

Most resource endpoints (Scenarios, Simulations, Assets, Teams) support both patterns. The Tenant ID determines which workspace the request operates on. Accessing a Tenant you are not authorized for returns a `403` response.

## What's next?

- [Filters reference](../reference/apis/filters.md) -- Complete filter format documentation
- [Getting started](../usage/getting-started.md) -- Platform usage overview
