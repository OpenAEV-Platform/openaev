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

## XTM One chat endpoints

When OpenAEV is connected to XTM One, the embedded chat panel reaches XTM One through proxy endpoints under `/api/xtmone/chat`. They require an Enterprise Edition license and call XTM One's embedded-chat API as the calling OpenAEV user, so XTM One applies that user's own permissions. The endpoints below manage the conversations of the chat history, the XTM One workspaces that group them, and the conversations a message references with `@`:

| Endpoint | Description |
|---|---|
| `POST /api/xtmone/chat/sessions` | Creates or restores a conversation. The body is forwarded to XTM One as sent, including `workspace_id` to file a new conversation into a workspace |
| `PATCH /api/xtmone/chat/sessions/{id}` | Renames a conversation (`title`) or files it into a workspace (`workspace_id`); an explicit `null` `workspace_id` takes it out of its workspace |
| `GET /api/xtmone/chat/workspaces` | Lists the user's XTM One workspaces |
| `POST /api/xtmone/chat/workspaces` | Creates a workspace (`name`, optional `description`) |
| `PATCH /api/xtmone/chat/workspaces/{id}` | Renames a workspace or changes its description |
| `DELETE /api/xtmone/chat/workspaces/{id}` | Deletes a workspace |
| `GET /api/xtmone/chat/conversation-references` | Lists the conversations the user may reference with `@`, those XTM One lets them open. Optional parameters: `q` (search text, trimmed and cut to 200 characters), `limit` (1 to 20) and `exclude` (the id of a conversation not to list, typically the current one). A malformed parameter is left out rather than refused |
| `POST /api/xtmone/chat/messages` | Sends a message and streams the answer as server-sent events. `referenced_conversation_ids` names the conversations the message references: only an array of UUIDs is read, and its first 5 distinct ids are forwarded |

Path `{id}` values must be UUIDs, otherwise the request is refused with a `400`. Except for `POST /api/xtmone/chat/messages`, these endpoints answer with XTM One's own status, and when XTM One refuses a change (for example the user's default workspace, or a workspace that still holds work items, cannot be deleted), with a body holding only XTM One's `detail` message. A `422` means XTM One refused the request, possibly because of an error between OpenAEV and XTM One: the reason is in the response body's `detail`.

Restoring a conversation (`POST /api/xtmone/chat/sessions` with its `conversation_id`) returns its messages as XTM One serves them: a user message that referenced other conversations keeps their `conversation_refs` (`conversation_id`, `title`, `key`).

When XTM One is not configured on OpenAEV, every endpoint under `/api/xtmone/chat` answers `503 Service Unavailable` with the body `{"detail": "XTM One is not configured"}`.

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

## What's next?

- [Filters](filters.md) -- Filter format and operators
- [Profile](../../administration/profile.md) -- Where to manage your account
