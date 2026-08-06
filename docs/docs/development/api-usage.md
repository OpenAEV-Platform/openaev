# API usage

This page explains how to interact with the OpenAEV REST API programmatically when building integrations, scripts, or custom tools.

## Authentication

All API requests require authentication. Include your API key in the `Authorization` header:

```http
Content-Type: application/json
Authorization: Bearer <API_KEY>
```

Generate an API key from your user profile in the OpenAEV interface. The key inherits the permissions of the user who created it.

## Base URL

All endpoints are prefixed with `/api/`. In a multi-tenant setup, Tenant-scoped endpoints use `/api/tenants/{tenantId}/`.

## Common operations

### List resources

Most resources support a search endpoint that accepts pagination, sorting, and filtering:

```http
POST /api/scenarios/search
```

```json
{
  "page": 0,
  "size": 20,
  "textSearch": "",
  "sorts": [
    { "property": "scenario_name", "direction": "asc" }
  ],
  "filterGroup": {
    "mode": "and",
    "filters": []
  }
}
```

### Create a resource

Send a `POST` request with the resource data in the request body:

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

### Update a resource

Send a `PUT` request with the full resource data:

```http
PUT /api/scenarios/{scenarioId}
```

### Delete a resource

Send a `DELETE` request:

```http
DELETE /api/scenarios/{scenarioId}
```

## Discovering endpoints

Use the OpenAPI schema to discover all available endpoints and their request/response models:

- **Swagger UI**: `https://<your-openaev-url>/swagger-ui/index.html`
- **OpenAPI schema (JSON)**: `https://<your-openaev-url>/api-docs`

## Filtering

Search endpoints accept a `filterGroup` object for fine-grained queries. See the [Filters reference](../reference/apis/filters.md) for the complete filter format.

## Error handling

The API returns standard HTTP status codes. Handle errors based on the status code:

| Code | Meaning | Action |
|---|---|---|
| `400` | Validation failed | Check the `errors` object in the response for field-level messages |
| `401` | Authentication failed | Verify your API key |
| `403` | Insufficient permissions | Check the user's roles and capabilities |
| `404` | Resource not found | Verify the resource ID |
| `500` | Server error | Retry or report the issue |

## What's next?

- [REST API](../usage/rest-api.md) -- Full API documentation with pagination and filtering details
- [Filters reference](../reference/apis/filters.md) -- Filter format documentation
- [Platform development](platform.md) -- Set up a local development environment
