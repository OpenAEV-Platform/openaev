---
applyTo: "**/security/**,**/auth/**,**/config/Security*,**/aop/AccessControl*,**/service/Permission*,**/permissions/**"
description: "Security conventions: RBAC, @AccessControl, permission chain, security rules"
---

# Security Conventions

## @AccessControl (AOP aspect)

Every REST endpoint must have `@AccessControl`. See the annotation in `io.openaev.aop.AccessControl` for the full definition.

## Adding a new resource type

1. `ResourceType.java` — add enum value
2. `Capability.java` — add ACCESS/MANAGE/DELETE with parent hierarchy
3. `@AccessControl` on all endpoints
4. If grant-managed: add to `RESOURCES_MANAGED_BY_GRANTS`
5. If open for READ: add to `RESOURCES_OPEN`

## Never Do

- Never hardcode secrets, API keys, or credentials
- Never send raw error messages/stack traces to clients
- Never bypass `@AccessControl` without explicit `skipRBAC = true` and a comment explaining why
- Never return `tenant_id` in API responses
- Never use native `@Query` without `WHERE tenant_id = ...`
- Never assign platform-only capabilities to tenant roles or vice versa


