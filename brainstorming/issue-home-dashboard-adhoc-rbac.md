**Title**: `fix(dashboard): default home dashboard widgets return 403 for non-admin users`

**Labels**: `bug`, `needs triage`

---

## Description

The built-in default home dashboard loads its widgets through the ad-hoc dashboard endpoints
(`POST /api/tenants/{tenantId}/dashboards/adhoc/{series,count,average,entities,entities-runtime}`).
They are gated on **READ `TENANT_SETTING`**, i.e. the `ACCESS_TENANT_SETTINGS` capability, while the
rest of `DashboardApi` is gated on `DASHBOARD` (`ACCESS_DASHBOARDS`):

```java
@PostMapping("/adhoc/entities")
@AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
public EsEntities adHocEntities(TxCtx ctx, @Valid @RequestBody AdHocWidgetInput input)
```

A non-admin user whose role grants `ACCESS_DASHBOARDS` / `MANAGE_DASHBOARDS` but not
`ACCESS_TENANT_SETTINGS` gets a 403 on every home widget. `ACCESS_TENANT_SETTINGS` is an
administration capability (it also grants reads on tag rules, attack patterns, kill chain phases…), so
in practice most non-admin users cannot use the home page.

The gating was introduced with the home redesign (#6704, commit `ba89f5938`), copied from the stored
home-dashboard endpoints in `TenantSettingsApi` (`/home-dashboard/{count,average,series,entities,
entities-runtime,attack-paths}/{widgetId}`), which have the same `READ TENANT_SETTING` gate. The data
itself is already scoped per user by the engine, so the settings capability protects nothing extra.

## Environment

1. OS: macOS
2. Version: 3.261005.0 (local dev)
3. Other environment details: non-admin user, one group "Manager" with role "Manager"
   (ACCESS/MANAGE/DELETE_DASHBOARDS, no ACCESS_TENANT_SETTINGS, no BYPASS)

## Reproducible steps

1. Create a role with `ACCESS_DASHBOARDS` (and `MANAGE_DASHBOARDS`) but without
   `ACCESS_TENANT_SETTINGS`; assign it to a non-admin user through a group.
2. Log in as that user and open the home page (built-in default dashboard).
3. The widgets stay empty; the browser shows
   `POST /api/tenants/{tenantId}/dashboards/adhoc/entities` → `403 Forbidden`,
   `"Access denied for user: <email>"` (`AccessControlAspect.methodRBACVerification`).

## Expected output

Any user allowed to see the home page gets its widgets, with data scoped to their own grants (and
markings). The home-dashboard endpoints are gated on a dashboard capability (`READ DASHBOARD`), or on
tenant membership only, consistently with how the frontend shows the home page.

## Actual output

403 on every `/dashboards/adhoc/*` call (and on `/home-dashboard/*` when a stored dashboard is set as
home) unless the user holds `ACCESS_TENANT_SETTINGS`.

## Additional information

- `openaev-api/src/main/java/io/openaev/rest/dashboard/DashboardApi.java` — `/adhoc/*` endpoints.
- `openaev-api/src/main/java/io/openaev/rest/settings/TenantSettingsApi.java` — `/home-dashboard/*` endpoints.
- `openaev-model/src/main/java/io/openaev/database/model/Capability.java` — `ACCESS_TENANT_SETTINGS`
  vs `ACCESS_DASHBOARDS`.
- To check while fixing: the frontend permission check that decides whether the home page is shown
  (CASL `ability.can`), so backend and frontend use the same capability.
- Workaround: add `ACCESS_TENANT_SETTINGS` to the user's role.
