# Implementation Plan — US.1 / US.2 / US.3 / US.4

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
