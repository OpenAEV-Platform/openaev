# RBAC Capability Segregation and No-Escalation — Technical Design (US.1/US.2/US.3/US.5/US.6)

## Scope

This design covers:

1. **US.1**: Extract tenant users/groups/roles permissions from `*_TENANT_SETTINGS` into a dedicated tenant triad.
2. **US.2**: Enforce tenant no-escalation (cannot grant capabilities you do not hold).
3. **US.3**: Enforce the same no-escalation rule on platform flows.
4. **US.5**: Frontend visibility — capabilities the actor does not hold are shown disabled/greyed out wherever role capabilities can be viewed or edited, and cannot be added/removed/toggled.
5. **US.6**: Frontend blocking — role-to-group and user-to-group assignment is blocked client-side with a clear error message when it would escalate privileges, backend remains the source of truth.

This document intentionally excludes implementation planning details.

## Design Goals

1. **Least privilege by capability shape**: tenant settings management must not implicitly grant identity and access administration.
2. **No privilege escalation**: role/group/user assignment operations cannot increase another user's effective privileges beyond the actor's own effective privileges.
3. **Parity**: tenant and platform paths use the same authorization model and guard semantics.
4. **Backend as source of truth, UI as feedback layer**: US.5/US.6 add no new authorization logic — they only reflect the guard rules already enforced server-side (US.2/US.3), so the UI can never be more permissive than the API.

## Key Decisions

### 1. Dedicated tenant capability triad (US.1)

Create tenant-side equivalents of the existing platform triad:

- `ACCESS_TENANT_USERS_GROUPS_AND_ROLES`
- `MANAGE_TENANT_USERS_GROUPS_AND_ROLES`
- `DELETE_TENANT_USERS_GROUPS_AND_ROLES`

Move `USER`, `USER_GROUP`, and `GROUP_ROLE` resource-action pairs from `*_TENANT_SETTINGS` to the new triad.

### 2. Unified no-escalation guard (US.2 + US.3)

Apply one shared guard rule across tenant and platform operations where effective permissions may increase:

- Role create/update
- Attach roles to group
- Attach users to group

Guard principle:

- Actor must hold all non-`BYPASS` capabilities being granted.
- If granted set contains `BYPASS`, actor must literally hold `BYPASS` in the relevant scope.

> NOTE: A user can only give permissions they already have themselves.
> There is one special case: BYPASS.
> Even if someone has many normal permissions, they still cannot grant BYPASS unless they also personally have BYPASS.
> So the system checks:
> 1. For regular permissions: “Do you have all of these?”
> 2. If BYPASS is in the set: “Do you explicitly have BYPASS too?”

### 3. Update semantics (resolved)

For role updates, the guard is evaluated on the **full resulting capability set** (not diff-only additions).

Rationale:

- Prevents bypass through replacement payload tricks.
- Keeps behavior deterministic and consistent between create and update.
- Simplifies reasoning for tenant and platform parity.

### 4. Frontend capability-aware rendering (US.5)

The role/capability editor computes, client-side, the current actor's effective capability set and cross-references it against the capabilities displayed for a role:

- **Where the actor's capabilities come from**: `User.getCapabilities()` (backend) is serialized as `user_capabilities` on the `/me` payload. `root.tsx` reads `me.user_capabilities` and passes it into `PermissionsProvider`, which builds a CASL `AppAbility` (`defineAbility(capabilities, grants, isAdmin)`) exposed app-wide via `AbilityContext`/`useAbility()`. This is already fetched once at session bootstrap — no new endpoint or additional API call is required for US.5/US.6.
- The role editor consumes this same ability/capability list (already used elsewhere for `Can`/`useAbility` checks) to know which capability enum values the actor holds, then renders any capability **not** in that set as disabled/greyed out.
- A capability checkbox/row not present in the actor's effective set is rendered **disabled and greyed out**, with tooltip "Grant capability is disabled because you don't have access to the required capabilities.", regardless of whether the role currently holds it or not.
- This applies uniformly whether the role being edited already has that capability (e.g. viewing the Admin role) or does not.
- No new backend endpoint is required: the actor's effective capabilities are already available; this is a pure rendering/interaction rule in the existing capability-editing component(s).
- This is presentation-only — it does not replace or weaken the backend no-escalation guard (US.2/US.3), which remains authoritative even if a disabled control were somehow bypassed (e.g. direct API call).
- **Staleness caveat**: `user_capabilities` is a snapshot from session bootstrap (or the last `/me` refresh). If the actor's capabilities change mid-session (e.g. an admin edits their role while they're logged in), the UI check can be stale until the next `/me` refresh — this is acceptable since the backend guard (US.2/US.3) is authoritative regardless.

### 5. Frontend blocking + error surfacing (US.6)

Before submitting a role-to-group or user-to-group assignment, the frontend pre-checks the same rule the backend guard enforces (US.2/US.3), so the user gets immediate feedback without a round-trip 403:

- **Role-to-group assignment**: if the role being attached contains at least one capability outside the actor's effective set, block submission client-side.
- **User-to-group assignment**: if the target group's effective capabilities (union of all its roles) contain at least one capability outside the actor's effective set, block submission client-side.
- **Error message**: "You can't do this operation because you don't have access to the required capabilities."
- This is a **defense-in-depth / UX layer only**: the backend guard is still invoked and still authoritative. If the frontend pre-check is bypassed or out of sync (e.g. stale client-side capability cache), the backend still returns 403 for the same rule, and the frontend should surface that 403 with the same error message.

## Authorization Flow Overview

1. **AccessControl gate** first validates operation-level capability (e.g., `GROUP_ROLE:CREATE` resolves to the new tenant triad capability after US.1).
2. **No-escalation guard** then validates grantability against actor effective capabilities.
3. **Business service** performs persistence only if both checks pass.

## Sequence Diagrams

### Flow A — Tenant role management denied after capability split (US.1)

```mermaid
sequenceDiagram
    actor U as Tenant user (MANAGE_TENANT_SETTINGS only)
    participant API as TenantRoleApi
    participant AOP as AccessControlAspect
    participant SVC as RoleService

    U->>API: POST /roles (GROUP_ROLE, CREATE)
    API->>AOP: Resolve required capability from resource/action
    AOP->>AOP: GROUP_ROLE+CREATE -> MANAGE_TENANT_USERS_GROUPS_AND_ROLES
    AOP-->>API: Deny (user lacks dedicated triad capability)
    API-->>U: 403 Forbidden
    Note over SVC: Service is never invoked
```

### Flow B — Tenant role create/update with no-escalation guard (US.2)

```mermaid
sequenceDiagram
    actor U as Tenant actor
    participant API as TenantRoleApi
    participant SVC as RoleService
    participant G as EscalationGuard
    participant DB as RoleRepository

    U->>API: POST/PUT role with capability set C
    API->>SVC: create/update role
    SVC->>G: assertCanGrant(actor=U, granted=C)
    alt C contains BYPASS and actor has no BYPASS
        G-->>SVC: AccessDeniedException
        SVC-->>API: deny
        API-->>U: 403 Forbidden
    else C(non-BYPASS) not subset of actor effective capabilities
        G-->>SVC: AccessDeniedException
        SVC-->>API: deny
        API-->>U: 403 Forbidden
    else grantable
        G-->>SVC: OK
        SVC->>DB: persist role
        DB-->>SVC: saved role
        SVC-->>API: role
        API-->>U: 200/201
    end
```

### Flow C — Tenant group assignment path with transitive escalation check (US.2)

```mermaid
sequenceDiagram
    actor U as Tenant actor
    actor T as Target user
    participant API as TenantGroupApi
    participant SVC as TenantGroupService
    participant G as EscalationGuard

    U->>API: PUT group roles (roleIds)
    API->>SVC: updateGroupRoles
    SVC->>G: assertCanGrant(actor=U, granted=union(role capabilities))
    G-->>SVC: allow or deny
    SVC-->>API: 200 or 403

    U->>API: PUT group users (add T)
    API->>SVC: updateUsers
    SVC->>G: assertCanGrant(actor=U, granted=group effective capabilities)
    G-->>SVC: allow or deny
    SVC-->>API: 200 or 403
```

**Flow C clarification (resulting-state check):**

- For `updateGroupRoles`, `granted = union(role capabilities)` means:
  - compute the group role set **after** applying the request (`resultingRoles`),
  - then compute `resultingCapabilities = union(capabilities of resultingRoles)`,
  - then require actor `U` to be allowed to grant the full `resultingCapabilities` set.
- This includes capabilities from roles already on the group that remain, plus the newly added role(s).
- So when adding a new role to an existing group, the check is performed on the **entire resulting group capability set**, not only on the delta.
- For `updateUsers`, use the same principle: evaluate the target group's **current effective capabilities** (union of all roles currently attached to that group), then require actor `U` to be allowed to grant that full set before adding users.

### Flow D — Assign user to group (sequential AccessControl then no-escalation guard)

```mermaid
sequenceDiagram
    actor U as Tenant actor
    actor T as Target user
    participant API as TenantGroupApi
    participant AOP as AccessControlAspect
    participant SVC as TenantGroupService
    participant G as EscalationGuard
    participant DB as GroupRepository

    U->>API: PUT /groups/{id}/users (add T)
    API->>AOP: Check USER_GROUP, WRITE capability
    alt Actor lacks required capability
        AOP-->>API: deny
        API-->>U: 403 Forbidden
    else AccessControl passed
        AOP-->>API: allow
        API->>SVC: updateUsers(groupId, userIds)
        SVC->>SVC: resolve group effective capabilities (all roles union)
        SVC->>G: assertCanGrant(actor=U, granted=group effective capabilities)
        alt Escalation check fails
            G-->>SVC: AccessDeniedException
            SVC-->>API: deny
            API-->>U: 403 Forbidden
        else Escalation check passes
            G-->>SVC: OK
            SVC->>DB: save group membership
            DB-->>SVC: updated group
            SVC-->>API: success
            API-->>U: 200 OK
            Note over T: T now inherits group capabilities
        end
    end
```

### Flow E — Platform parity with tenant guard semantics (US.3)

```mermaid
sequenceDiagram
    actor U as Platform actor
    participant API as PlatformRoleApi / PlatformGroupApi
    participant SVC as PlatformRoleService / PlatformGroupService
    participant G as EscalationGuard

    U->>API: Create/update platform role or group assignment
    API->>SVC: Operation request
    SVC->>G: assertCanGrant(actor=U, granted=requested/effective set)
    alt Not grantable
        G-->>SVC: AccessDeniedException
        SVC-->>API: deny
        API-->>U: 403 Forbidden
    else Grantable
        G-->>SVC: OK
        SVC-->>API: success
        API-->>U: 200/201
    end
```

### Flow F — Frontend pre-check blocks unauthorized assignment (US.5 + US.6)

```mermaid
sequenceDiagram
    actor U as Tenant/Platform actor (browser)
    participant UI as Role/Group editor (frontend)
    participant API as TenantGroupApi / PlatformGroupApi
    participant SVC as GroupService
    participant G as EscalationGuard

    Note over UI: On render, UI greys out/disables any capability<br/>not in actor's effective capability set (US.5)

    U->>UI: Attach role R to group / add user to group
    UI->>UI: Compute target capability set (role R, or group effective capabilities)
    alt Target set not subset of actor effective capabilities
        UI-->>U: Block submit, show "You can't do this operation because you don't have access to the required capabilities." (US.6)
        Note over API,SVC: Request never sent
    else Client-side check passes (or bypassed, e.g. direct API call)
        UI->>API: PUT group roles/users
        API->>SVC: updateGroupRoles / updateUsers
        SVC->>G: assertCanGrant(actor=U, granted=resulting/effective set)
        alt Guard denies (authoritative)
            G-->>SVC: AccessDeniedException
            SVC-->>API: deny
            API-->>UI: 403 Forbidden
            UI-->>U: Show same error message
        else Guard allows
            G-->>SVC: OK
            SVC-->>API: success
            API-->>UI: 200 OK
            UI-->>U: Assignment confirmed
        end
    end
```

## Non-Goals

- No implementation task breakdown in this document.
- No rollout checklist, estimates, or ownership allocation.
