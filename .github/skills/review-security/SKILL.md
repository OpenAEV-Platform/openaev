---
name: review-security
description: >-
  Security review checklist for OpenAEV code: RBAC, tenant isolation, data exposure,
  authentication. Use when reviewing PRs or auditing security of a feature.
---

# Security Review

## Procedure

### Step 1 — Check @AccessControl coverage

- Every REST endpoint MUST have `@AccessControl`
- Verify `resourceType` matches the entity being accessed
- Verify `actionPerformed` matches the HTTP method semantics
- If `skipRBAC = true` is used, verify there's a comment explaining why

### Step 2 — Check tenant isolation

- All `TenantBase` entities must have `@Filter(name = "tenantFilter")`
- Search for any native `@Query` — they bypass the filter:
  ```bash
  grep -rn "nativeQuery = true" openaev-model/ openaev-api/
  ```
- Each native query MUST include `WHERE tenant_id = :tenantId` or join via tenant
- Tenant relation must be `@JsonIgnore` — never in API output

### Step 3 — Check data exposure

- New controllers must use Output DTOs — never return JPA entities directly
- Swagger annotations must be explicit for LAZY relations:
  `@ArraySchema(schema = @Schema(type = "string"))` when returning IDs
- No `tenant_id` in any JSON response
- No stack traces or internal error details exposed to clients

### Step 4 — Check authentication & authorization

- Protected endpoints require valid session (Spring Security)
- Admin-only operations: `ResourceType.UNKNOWN` or explicit admin check
- Grant-managed resources: verify they're in `RESOURCES_MANAGED_BY_GRANTS`
- `isUserHasAccess()` returns meaningful logic, not just `return true`

### Step 5 — Check secrets & credentials

```bash
grep -rn "password\|secret\|api_key\|apiKey\|token" --include="*.java" --include="*.ts" src/
```

- No hardcoded credentials
- Secrets in `application.properties` use environment variables
- No `.env` files committed

### Step 6 — Report

Document findings using conventional comments format:
- `issue (blocking):` for security vulnerabilities
- `suggestion (non-blocking):` for improvements
- `note:` for informational items

