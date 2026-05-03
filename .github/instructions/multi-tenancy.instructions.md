---
applyTo: "openaev-model/src/main/java/**/*.java,openaev-api/src/main/java/**/*.java,**/migration/**"
description: "Multi-tenancy conventions: tenant isolation, dual-scope, anti-patterns"
---

# Multi-Tenancy Conventions

## Entity Scoping

| Tenant-scoped | Platform-level | Dual-scope (Settings, User, Role, Group) |
|---------------|----------------|------------------------------------------|
| `TenantBase` + `@Filter("tenantFilter")` + `TenantBaseListener` | `Base` + `ModelBaseListener` | `DualScopeBase` + `ModelBaseListener` |
| `tenant_id NOT NULL` | No `tenant_id` | `tenant_id` **NULLABLE** |
| Unique: composite `(field, tenant_id)` | Simple unique | Partial unique indexes |
| Single service + single API | Single service + single API | Two services + two APIs |

## Dual-Scope Pattern

- Entity implements `DualScopeBase` — `@Nullable getTenant()` / `setTenant()`
- Repository keeps generic JPA methods (`findByKey()`, `findAll()`, etc.) and adds explicit platform-scoped methods (`findByKeyAndTenantIsNull()`, `findAllByTenantIsNull()`) and tenant-scoped methods (`findByKeyAndTenantId()`, `findAllByTenantId()`)
- Two services: `PlatformXxxService` (uses `*TenantIsNull` methods, never receives tenantId) / `TenantXxxService` (uses `*TenantId` methods, always receives tenantId)
- Two APIs: `/api/platform-{entities}` / `TENANT_PREFIX + "/{entities}"`

## Critical Rules

1. Native `@Query` **bypasses** Hibernate filter → always add `WHERE tenant_id = :tenantId`
2. Never return `tenant_id` in API responses → `@JsonIgnore` on tenant relation
3. Unique constraints on tenant-scoped entities → composite `(field, tenant_id)`

## Anti-Patterns

| ❌ Don't | ✅ Do |
|----------|-------|
| Native query without `WHERE tenant_id` | Add explicit tenant clause |
| `@Column(unique = true)` on tenant-scoped field | Composite `UNIQUE (field, tenant_id)` |
| Single service for dual-scope entity | Split `PlatformXxxService` / `TenantXxxService` |
| `tenant_id` in API response | `@JsonIgnore` on tenant relation |
