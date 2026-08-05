# Implementation Plan — US.1 / US.2 / US.3 / US.4 / US.5 / US.6

## US.1 — Extract tenant users/groups/roles capability triad out of `MANAGE_TENANT_SETTINGS`

### Subtasks

1. **Capability model + access-control alignment (tenant triad split)**
   - Add `ACCESS_TENANT_USERS_GROUPS_AND_ROLES`, `MANAGE_TENANT_USERS_GROUPS_AND_ROLES`, and `DELETE_TENANT_USERS_GROUPS_AND_ROLES`.
   - Move `USER`, `USER_GROUP`, and `GROUP_ROLE` mappings from tenant settings capabilities to the new tenant triad.
   - Align `@AccessControl` resolution so tenant role/group/user-management operations now require the new tenant triad from subtask 1.
   - Validate endpoint-level checks resolve via `Capability.of(resourceType, action)` to the new triad.
   - Confirm no accidental access regression on unrelated tenant settings resources.
   - **Context:** Runtime sequence is: request hits role/group API -> `AccessControlAspect` resolves required capability from `(resourceType, action)` -> if actor has only `MANAGE_TENANT_SETTINGS`, access is denied with `403` -> service layer is not executed. After triad split, required capability becomes `MANAGE_TENANT_USERS_GROUPS_AND_ROLES`.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`

2. **Data migration for existing roles**
   - Add migration to grant equivalent new tenant triad capabilities to roles that currently rely on tenant settings capabilities for user/group/role administration.
   - Preserve current admin behavior during upgrade.
   - **Context:** Without migration, roles that previously worked via `*_TENANT_SETTINGS` lose access after moving `USER`/`USER_GROUP`/`GROUP_ROLE` mappings to the new triad. Migration must preserve equivalent effective access for existing tenant admin roles.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`

3. **Capability catalog and UI contract update**
   - Update capability group labels/translations for the new tenant triad.
   - Regenerate API/client capability contracts impacted by new enum values.
   - **Context:** Backend enum changes introduce new capability group and values; frontend capability tree, labels, and generated API types must include these values so UI permissions and role editing remain consistent with backend enforcement.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`

## US.2 — Tenant no-escalation rule (cannot grant capabilities not held)

### Subtasks

1. **Shared guard contract**
   - Define one reusable escalation-guard contract for grant operations.
   - Include explicit `BYPASS` rule: actor must literally hold `BYPASS` to grant `BYPASS`.
   - Apply non-`BYPASS` subset rule against actor effective capabilities.
   - **Context:** Guard sequence is: AOP access check passes -> service computes granted capability set -> guard validates grantability -> persistence occurs only on success. Guard rules are: actor must hold all non-`BYPASS` capabilities; if target contains `BYPASS`, actor must explicitly hold `BYPASS`.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`

2. **Tenant role create/update enforcement**
   - Enforce guard in tenant role create and update service paths.
   - Use the resolved decision: evaluate against the **full resulting capability set** on update.
   - **Context:** For role create/update, runtime order is sequential: API -> `AccessControlAspect` -> service -> escalation guard -> repository save. On update, validation is done on the full resulting role capability set (not delta-only), preventing bypass via full replacement payload.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`

> 🎯 **Out of scope:** no need of datapack migration as pre-defined "manager" role does not have MANAGE_TENANT_SETTINGS and "admin" role uses TENANT_BYPASS, therefore no migration needed for DataPack.

3. **Tenant group-based grant enforcement**
   - Enforce guard on role-to-group assignment using union of assigned role capabilities.
   - Enforce guard on user-to-group assignment using group effective capabilities.
   - **Context:** For `updateGroupRoles`, compute `resultingRoles` after applying request, then validate actor against `union(capabilities(resultingRoles))`. For `updateUsers`, validate actor against current group effective capabilities before adding users. This ensures actor can grant the full privilege envelope the group conveys.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`

4. **Tenant e2e tests (Playwright)**
   - Add/adjust Playwright e2e tests for allow/deny scenarios, including `BYPASS`, non-`BYPASS`, and mixed capability sets across role and group assignment flows.
   - **Context:** E2E must cover real sequence and outcomes for tenant flows: role create/update and group role/user assignments, including both success and `403` denial paths, with explicit cases for normal capabilities and `BYPASS`.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`

## US.3 — Platform no-escalation rule parity

### Subtasks

1. **Platform integration of shared guard**
   - Reuse the same guard implementation used by tenant path (no forked logic).
   - Keep platform-specific scope handling while preserving identical rule semantics.
   - **Context:** Platform path must follow the same grant rules and call order as tenant path so behavior does not drift: AOP access check first, then shared escalation guard, then persistence.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`

2. **Platform role create/update enforcement**
   - Apply guard in platform role create and update service paths.
   - Keep update behavior aligned with tenant: full resulting capability set validation.
   - **Context:** Platform role create/update must use the same semantics as tenant: validate full resulting capability set on update; require explicit actor `BYPASS` to grant `BYPASS`; deny with `403` before persistence when checks fail.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`
> 🧠 **To explore in the app** which message should we return when a user attempts an escalation; ensure a clear error message is returned when escalation is detected.

3. **Platform group-based grant enforcement**
   - Enforce guard on platform role-to-group and user-to-group assignment paths.
   - Ensure transitive capability checks mirror tenant behavior.
   - **Context:** Platform group operations mirror tenant logic: on role assignment validate union of resulting group-role capabilities; on user assignment validate group effective capabilities before membership changes.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`

4. **Platform e2e tests (Playwright)**
   - Add/adjust Playwright e2e tests for allow/deny cases across platform role/group assignment paths, including explicit `BYPASS` checks.
   - **Context:** E2E must prove platform parity with tenant across role and group assignment operations, including grant success, escalation denial, and explicit `BYPASS` grant rules in UI/API end-to-end flow.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`

## US.4 — Cross-scope test matrix (tenant + platform)

> 🚧🛠 **ToDo** Soumaya to proposed an AI-driven Acceptance criteria test generation agent.

## US.5 — UI improvement for capability triad visibility and restrictions

### Subtasks

1. **Disabled/greyed-out rendering + interaction blocking for capabilities the actor doesn't hold**
   - In the role capability editor, cross-reference each displayed capability against the current actor's effective capability set (already exposed as `user_capabilities`).
   - Render any capability outside that set as disabled/greyed out, with tooltip "Grant capability is disabled because you don't have access to the required capabilities.", whether or not the role currently holds it.
   - Apply consistently everywhere role capabilities can be viewed or edited (role create, role edit, e.g. opening the Admin role).
   - Ensure disabled capability controls cannot be clicked, checked, unchecked, or otherwise mutated in the role editor state — a capability being greyed out must also be non-interactive.
   - Cover the case of opening a role that already contains capabilities the actor lacks: those remain visible but immutable.
   - **Context:** This is presentation-only — no new backend endpoint is needed since the actor's effective capabilities are already available client-side. It does not replace or weaken the backend no-escalation guard (US.2/US.3), which stays authoritative even if a disabled control were bypassed (e.g. a direct API call). Rendering and interaction-blocking are grouped because a capability that is greyed out but still clickable would defeat the purpose — both must ship together.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`

## US.6 — UI error when assigning unauthorized roles or users

### Subtasks

1. **Client-side pre-check for role-to-group assignment**
   - Before submitting a role-to-group assignment, compute the role's capability set and compare it against the actor's effective capabilities.
   - If the role contains at least one capability the actor doesn't hold, block submission client-side instead of relying solely on the server round-trip.
   - **Context:** This pre-check mirrors the same rule the backend guard enforces server-side (US.2/US.3: `updateGroupRoles` validates the union of resulting role capabilities against the actor). The frontend check is a UX layer only — the backend guard is still invoked on submit and remains authoritative.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`
> ❓ Question: do we need a pre-check? handling 403 from BE could be enough
2. **Client-side pre-check for user-to-group assignment**
   - Before submitting a user-to-group assignment, compute the group's effective capabilities (union of all its roles) and compare against the actor's effective capabilities.
   - If the group's effective capabilities contain at least one capability the actor doesn't hold, block submission client-side.
   - **Context:** Mirrors the backend guard's `updateUsers` check (US.2/US.3: validates the target group's current effective capabilities before adding users). Frontend check only — backend guard remains authoritative on submit.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`
> ❓ Question: do we need a pre-check? handling 403 from BE could be enough
3. **Unified error message and 403 fallback handling**
   - Display "You can't do this operation because you don't have access to the required capabilities." consistently for both role-to-group and user-to-group flows, whether the block originates from the client-side pre-check or from a backend 403 response.
   - Ensure the backend remains source of truth: if the client-side pre-check is bypassed or stale (e.g. cached capability set), the backend 403 must still be caught and surfaced with the same message rather than a generic/raw error.
   - **Context:** Backend restriction is the source of truth (US.2/US.3); this subtask only adds the UI feedback layer for already-covered authorization cases, ensuring the message is identical regardless of whether the block happened client-side or via a 403 from the escalation guard.
   - **DoD:**
     - Unit test
     - Integration test (API level)
     - Under feature flag `rbac-no-escalation`
