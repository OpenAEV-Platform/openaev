You are adding RBAC permissions for a new resource type in OpenAEV.

## Backend

### 1. `ResourceType.java` — add enum value

### 2. `Capability.java` — add hierarchy (ACCESS → MANAGE → DELETE, + LAUNCH if needed)

```java
ACCESS_MY_FEATURE(null,
                  pair(ResourceType.MY_NEW_TYPE, Action.READ),

pair(ResourceType.MY_NEW_TYPE, Action.SEARCH)),

MANAGE_MY_FEATURE(ACCESS_MY_FEATURE,
                  pair(ResourceType.MY_NEW_TYPE, Action.WRITE),

pair(ResourceType.MY_NEW_TYPE, Action.CREATE)),

DELETE_MY_FEATURE(MANAGE_MY_FEATURE,
                  pair(ResourceType.MY_NEW_TYPE, Action.DELETE)),
```

### 3. `PermissionService.java` — configure access model

- Grant-managed (like Scenario): add to `RESOURCES_MANAGED_BY_GRANTS`
- Open for READ (like Player): add to `RESOURCES_OPEN`
- Sub-resource (like Inject): add to `RESOURCES_USING_PARENT_PERMISSION`
- Standard capability-based: no change (handled by `Capability.of()` lookup)

### 4. Controllers — `@AccessControl` on every endpoint

## Frontend

### 5. `types.ts` — add SUBJECT if new category

```typescript
export const SUBJECTS = {...existing, MY_FEATURE: 'MY_FEATURE'} as const;
```

Parser auto-maps `ACCESS_MY_FEATURE` → `[ACCESS, MY_FEATURE]`.

### 6. Use in components

```typescript
const canAccess = ability.can(ACTIONS.ACCESS, SUBJECTS.MY_FEATURE);
```

### 7. For grant-based resources, create a permission hook (follow `useScenarioPermissions.ts`)

```typescript
const canAccess = ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, resourceId)
    || ability.can(ACTIONS.ACCESS, SUBJECTS.MY_FEATURE);
```

