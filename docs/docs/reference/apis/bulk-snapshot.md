# Bulk snapshot export

The bulk snapshot export is a pair of REST API endpoints that let an external system read the
current, verified state of every Endpoint in a Tenant as a machine-readable feed. It is designed for
the OpenGRC connector and other Governance, Risk and Compliance (GRC) integrations: instead of
replaying every Simulation, a consumer polls these endpoints and reads a current-state inventory,
differentially, one page at a time.

!!! note "Preview feature"

    The bulk snapshot export is a preview feature, off by default. See
    [Enabling the feature](#enabling-the-feature) below.

## Before you start

- **Enable the `BULK_SNAPSHOT_EXPORT` preview feature.** See
  [Enabling the feature](#enabling-the-feature).
- **Create a dedicated, non-administrator user** for the connector, and grant it the
  `Access observation snapshots` capability. See
  [Users and RBAC](../../administration/users-and-rbac.md) for how to create users, roles and
  capabilities. The connector authenticates with that user's API token.

!!! warning "The account must not be an administrator"

    Administrators bypass the platform's row-level restriction filtering entirely, so an
    administrative service account would return documents the connector is not meant to see, and
    would mask any misconfiguration of its grants.

!!! note "Unrestricted documents are tenant-wide"

    A document that belongs to no grantable resource — typically a Finding produced outside any
    Scenario or Simulation — carries no restriction, and the platform returns unrestricted documents
    to every holder of the `Access observation snapshots` capability in the Tenant, whatever their
    individual grants. Grant this capability only to accounts meant to see the whole Tenant
    inventory.

### Enabling the feature

The feature is controlled by the `openaev.enabled-dev-features` property, which is empty by
default:

```properties
openaev.enabled-dev-features=BULK_SNAPSHOT_EXPORT
```

The value is a comma-separated list of preview feature names. If the property already has a value on
your platform, extend it rather than replacing it, for example
`openaev.enabled-dev-features=SOME_OTHER_FEATURE,BULK_SNAPSHOT_EXPORT`.

#### note "Legacy property name"

    The legacy `openbas.enabled-dev-features` key is also read, and takes precedence over
    `openaev.enabled-dev-features` when set. Platforms still on the legacy key can enable the
    feature there instead.

## Endpoints

Both endpoints are `POST` requests, scoped to a Tenant, and take the same request body and the same
response envelope:

```http
POST /api/tenants/{tenantId}/snapshot/attack-observations/search
POST /api/tenants/{tenantId}/snapshot/vulnerability-observations/search
```

### Request body

All fields are optional.

| JSON field | Type | Default | Bounds |
|---|---|---|---|
| `cursor` | string | `null` | Opaque resume token from a previous page. Mutually exclusive with `since` — sending both returns a **400**. |
| `since` | ISO-8601 instant | `null` | Full reconciliation lower bound. Mutually exclusive with `cursor`. |
| `page_size` | integer | `500` | Silently clamped to `[1, 1000]`. |
| `safety_lag_seconds` | integer | `120` | Silently clamped to `[max(60, grace), 3600]`, where `grace` is the `engine.indexing-grace-window-seconds` [configuration parameter](../../deployment/configuration.md). |

Clamping is silent: an out-of-range `page_size` or `safety_lag_seconds` is corrected rather than
rejected. A client that sends `page_size: 5000` receives 1000 items, not an error.

### Response envelope

| JSON field | Meaning |
|---|---|
| `observations` | The page, at most `page_size` items. |
| `next_cursor` | Opaque resume token for the next page. An empty page echoes back the `cursor` it was given, so this is safe to store unconditionally. `null` only when the walk has no cursor yet (a `since` walk that matched nothing). |
| `has_more` | `true` when the page came back exactly full, meaning another page is expected to follow. |
| `snapshot_window_end` | Inclusive upper bound of the window this page covers: `min(now - safety_lag, indexed_through)`. |
| `indexed_through` | Approximate indexing horizon of this stream. A readiness signal, not the exact indexing cursor — see [Consistency and the safety lag](#consistency-and-the-safety-lag). |
| `server_time` | The single instant the whole response was computed from. |
| `consistency_mode` | Always `"eventual"`. |
| `snapshot_ready` | `true` when `indexed_through` is current with `now - safety_lag`, i.e. the window is not lagging beyond its own safety margin. |

## Attack observations

An attack observation is one document per Tenant, Asset, attack pattern, expectation type and
Scenario: the latest verified state of one technique on one Endpoint against one Scenario.

| JSON field | Meaning |
|---|---|
| `id` | Observation id. |
| `updated_at` | Last update timestamp. |
| `asset_id` | Asset id. |
| `scenario_id` | Scenario id. |
| `last_simulation_id` | Id of the last Simulation that produced this observation. |
| `platforms_reporting` | Security platforms that reported on this technique. |
| `asset_name` | Asset name. |
| `endpoint_hostname` | Endpoint hostname. |
| `endpoint_platform` | Endpoint platform. |
| `tenant_name` | Tenant name. |
| `attack_pattern_external_id` | Attack pattern external id. |
| `attack_pattern_name` | Attack pattern name. |
| `scenario_name` | Scenario name. |
| `last_simulation_name` | Name of the last Simulation that produced this observation. |
| `expectation_type` | Either `PREVENTION` or `DETECTION`. |
| `expectation_status` | `SUCCESS` when `attempts_success` equals `attempts_total`, `FAILED` when `attempts_success` is `0`, `PARTIAL` otherwise. Never `PENDING` nor `UNKNOWN`. |
| `attempts_total` | Number of attempts carrying a verdict. Attempts still awaiting one are not counted, so a partially executed replay does not deflate `coverage_ratio`. |
| `attempts_success` | Number of those attempts that met the expected score. |
| `coverage_ratio` | `attempts_success` divided by `attempts_total`, between `0` and `1`. |
| `platforms_succeeded` | Security platforms that succeeded. |
| `last_verified_at` | Last verification timestamp. |

`platforms_reporting` is every security platform that returned a result for this technique, while
`platforms_succeeded` is the subset of those that detected or prevented it. The difference between
the two is what the interface calls "Missed by security platform".

## Vulnerability observations

A vulnerability observation is one document per Tenant, Asset and vulnerability: the latest known
state of one vulnerability on one Endpoint.

| JSON field | Meaning |
|---|---|
| `id` | Observation id. |
| `updated_at` | Last update timestamp. |
| `asset_id` | Asset id. |
| `last_finding_id` | Id of the Finding that defines the current state of this grain. |
| `last_scenario_id` | Id of the last Scenario that produced this observation, or `null` when the Finding was produced outside any Scenario. |
| `last_simulation_id` | Id of the last Simulation that produced this observation. |
| `finding_type` | Type of the Finding. Currently always `CVE` (Common Vulnerabilities and Exposures). |
| `finding_value` | Finding value. |
| `asset_name` | Asset name. |
| `endpoint_hostname` | Endpoint hostname. |
| `endpoint_platform` | Endpoint platform. |
| `tenant_name` | Tenant name. |
| `vulnerability_external_id` | Vulnerability external id. |
| `last_scenario_name` | Name of the last Scenario that produced this observation, or `null` when the Finding was produced outside any Scenario. |
| `last_simulation_name` | Name of the last Simulation that produced this observation. |
| `last_verified_at` | Last verification timestamp. |

`last_finding_id` is the Finding that defines the current state of the grain: one grain, one
Finding, by construction.

## Cursor semantics

A request omits both `cursor` and `since` to start a **full reconciliation**: a walk of the entire
current state, from the beginning, with no lower bound. A request sends `since` instead to start a
walk of the entire current state, restricted to documents updated at or after that instant. A
request sends `cursor` to resume a walk in progress, from the `next_cursor` of a previous page.

The cursor is opaque: do not parse it, store it beyond your own resume logic, or share it across
Tenants — a cursor is bound to the Tenant and the stream it was issued for, and a foreign-tenant or
malformed cursor returns a **400**.

!!! example "Full reconciliation, then resume by cursor"

    First request — full reconciliation, no `cursor` and no `since`:

    ```http
    POST /api/tenants/2cffad3a-0001-4078-b0e2-ef74274022c3/snapshot/attack-observations/search
    ```

    ```json
    {
      "page_size": 500
    }
    ```

    ```json
    {
      "observations": [ /* ... up to 500 items ... */ ],
      "next_cursor": "eyJ2IjoxLCJ0ZW5hbnQiOiIyY2ZmYWQzYS0wMDAxLTQwNzgtYjBlMi1lZjc0Mjc0MDIyYzMiLCJ0cyI6IjIwMjYtMDgtMjRUMDc6NTg6MDBaIiwiaWQiOiI3ZjNiMWM2Mi05ZDRhLTRmMTgtOGIyMS01YzBlOWE3ZDRlMzMifQ",
      "has_more": true,
      "snapshot_window_end": "2026-08-24T07:58:00Z",
      "indexed_through": "2026-08-24T07:59:30Z",
      "server_time": "2026-08-24T08:00:00Z",
      "consistency_mode": "eventual",
      "snapshot_ready": true
    }
    ```

    `has_more` is `true`, so the client resumes with the returned `next_cursor`:

    ```http
    POST /api/tenants/2cffad3a-0001-4078-b0e2-ef74274022c3/snapshot/attack-observations/search
    ```

    ```json
    {
      "cursor": "eyJ2IjoxLCJ0ZW5hbnQiOiIyY2ZmYWQzYS0wMDAxLTQwNzgtYjBlMi1lZjc0Mjc0MDIyYzMiLCJ0cyI6IjIwMjYtMDgtMjRUMDc6NTg6MDBaIiwiaWQiOiI3ZjNiMWM2Mi05ZDRhLTRmMTgtOGIyMS01YzBlOWE3ZDRlMzMifQ"
    }
    ```

    ```json
    {
      "observations": [],
      "next_cursor": "eyJ2IjoxLCJ0ZW5hbnQiOiIyY2ZmYWQzYS0wMDAxLTQwNzgtYjBlMi1lZjc0Mjc0MDIyYzMiLCJ0cyI6IjIwMjYtMDgtMjRUMDc6NTg6MDBaIiwiaWQiOiI3ZjNiMWM2Mi05ZDRhLTRmMTgtOGIyMS01YzBlOWE3ZDRlMzMifQ",
      "has_more": false,
      "snapshot_window_end": "2026-08-24T07:58:05Z",
      "indexed_through": "2026-08-24T07:59:35Z",
      "server_time": "2026-08-24T08:00:05Z",
      "consistency_mode": "eventual",
      "snapshot_ready": true
    }
    ```

    An empty page with `has_more: false` means the client has caught up; poll again after a delay.
    The cursor is echoed back unchanged, so `cursor = response.next_cursor` remains correct and the
    client does not need to remember an earlier one.

## Consistency and the safety lag

The served window never runs ahead of the platform's own indexing. `snapshot_window_end` is
`min(now - safety_lag, indexed_through)`, so no cursor can ever request data past what has been
indexed.

!!! note "`indexed_through` is an approximation"

    Once indexing is known to have passed `now - safety_lag`, the platform stops measuring the exact
    cursor and reports `now - grace` instead. That value can sit up to `safety_lag - grace` seconds
    ahead of the real cursor. It never affects what is served — `snapshot_window_end` is still
    capped at `now - safety_lag` — so treat `indexed_through` as a readiness signal, not as a
    precise measurement of indexing progress.

!!! note "`indexed_through` is a platform-wide signal"

    The indexing cursor is stored per stream, not per Tenant, and the indexer sweeps every Tenant
    together. So `indexed_through`, and therefore `snapshot_ready`, describe the platform's indexing
    backlog, not this Tenant's. On a busy shared platform, a quiet Tenant's window can be held back
    by another Tenant's write volume, with nothing in the response to explain why. See the warning
    in [Client obligations](#client-obligations) about not gating polling on `snapshot_ready`.

## Client obligations

A client integrating against this export must honor the following:

1. **The window is eventually consistent and deliberately behind wall-clock time.** The safety lag
   exists so that rows committed by long write transactions are not missed.
2. **Delivery is at-least-once.** Consumption must be idempotent on `id` and `updated_at` — the same
   document can legitimately be delivered twice.
3. **While `snapshot_ready` is `false`, the inventory is incomplete, so absence must not be read as a
   verdict.** Pages remain correct for the window they announce, and the served window is clamped to
   `indexed_through`, so no cursor can ever run ahead of the index.
4. **The polling loop must not be gated on `snapshot_ready`.** It is a data-interpretation signal, not
   a wait condition.

    !!! warning "Do not gate polling on `snapshot_ready`"

        A client that sleeps until `snapshot_ready` becomes `true` can starve permanently on a busy
        platform, because `indexed_through` is a platform-wide signal (see above) that a quiet Tenant
        does not control.

5. **Differential synchronization carries no deletions. A weekly full reconciliation is mandatory.**
   A request with both `cursor` and `since` omitted performs that full reconciliation, which is
   required to detect Endpoints or observations that no longer exist.
6. **The attack stream is Scenario-borne; the vulnerability stream is not.** An attack observation
   only exists for a technique verified within a Scenario, so an Endpoint exercised solely outside a
   Scenario is absent from the attack stream and reads as "never verified". A vulnerability
   observation is exported even when its Finding was produced outside any Scenario; in that case
   `last_scenario_id` and `last_scenario_name` are `null`.
7. **Absence has one overloaded meaning.** A grain missing from the stream may be out of scope, never
   verified, or deleted, and the export cannot tell these apart. This is exactly why the full
   reconciliation of obligation 5 is required.
8. **Labels are as of `last_verified_at`.** Renaming a Scenario propagates to an observation only on
   its next verdict, so a stale name is expected behaviour, not a bug.
9. **Attack patterns are attributed per Injector Contract.** A contract carrying three techniques,
   detected once, produces three separate `SUCCESS` documents — one per technique. A consumer that
   counts detections per technique must account for this.

## Errors

| Status | Cause |
|---|---|
| **400** | `since` and `cursor` sent together, or a malformed, unparseable, wrong-version, or foreign-tenant `cursor`. |
| **401** | No authentication. |
| **403** | Authenticated without the `Access observation snapshots` capability. |
| **404** | The `BULK_SNAPSHOT_EXPORT` preview feature is off. |

## What's next?

- [Users and RBAC](../../administration/users-and-rbac.md) — create the connector's user account and
  grant it the `Access observation snapshots` capability
- [Configuration](../../deployment/configuration.md) — the `engine.indexing-grace-window-seconds`
  parameter that bounds `safety_lag_seconds`
