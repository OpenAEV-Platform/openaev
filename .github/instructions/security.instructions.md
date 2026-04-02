---
applyTo: "openaev-api/src/main/java/**/*.java,openaev-model/src/main/java/**/*.java,openaev-front/src/**/*.ts,openaev-front/src/**/*.tsx"
description: "Security conventions: RBAC, @AccessControl, permission chain, security rules, tenant isolation"
---

# Security Conventions

## @AccessControl (AOP aspect)

Every REST endpoint must have `@AccessControl`. See the annotation in `io.openaev.aop.AccessControl` for the full definition.

## Adding a new resource type

### Backend

1. `ResourceType.java` — add enum value
2. `Capability.java` — add ACCESS/MANAGE/DELETE with parent hierarchy
3. `@AccessControl` on all endpoints
4. Configure access model in `PermissionService.java`:
   - Grant-managed (like Scenario): add to `RESOURCES_MANAGED_BY_GRANTS`
   - Open for READ (like Player): add to `RESOURCES_OPEN`
   - Sub-resource (like Inject): add to `RESOURCES_USING_PARENT_PERMISSION`
   - Standard capability-based: no change (handled by `Capability.of()` lookup)

### Frontend

5. `types.ts` — add SUBJECT if new category:
   ```typescript
   export const SUBJECTS = { ...existing, MY_FEATURE: 'MY_FEATURE' } as const;
   ```
   Parser auto-maps `ACCESS_MY_FEATURE` → `[ACCESS, MY_FEATURE]`.

6. Use in components:
   ```typescript
   const canAccess = ability.can(ACTIONS.ACCESS, SUBJECTS.MY_FEATURE);
   ```

7. For grant-based resources, create a permission hook (follow `useScenarioPermissions.ts`):
   ```typescript
   const canAccess = ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, resourceId)
       || ability.can(ACTIONS.ACCESS, SUBJECTS.MY_FEATURE);
   ```

## Tenant Isolation

- Every `TenantBase` entity must have `@Filter(name = "tenantFilter")`
- Native `@Query` bypasses the Hibernate filter — always add `WHERE tenant_id = :tenantId`
- Never return `tenant_id` in API responses — use `@JsonIgnore` on the tenant relation
- Never assign platform-only capabilities to tenant roles or vice versa

## Never Do

- Never hardcode secrets, API keys, or credentials
- Never send raw error messages/stack traces to clients
- Never bypass `@AccessControl` without explicit `skipRBAC = true` and a comment explaining why
- Never return `tenant_id` in API responses
- Never use native `@Query` without `WHERE tenant_id = ...`
- Never assign platform-only capabilities to tenant roles or vice versa
