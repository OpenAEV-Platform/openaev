---
name: add-tenant-isolation-test
description: >-
  Adds tenant isolation tests to an existing API test class. Verifies that
  tenant-scoped entities cannot leak between tenants (cross-tenant read, search,
  update, delete). Uses TenantIsolationTestHelper to set up real RBAC
  (capabilities/tenants/groups/roles) instead of isAdmin=true.
  Use when a tenant-scoped API (path contains /api/tenants/{tenantId}/...) is
  missing isolation tests.
---

# Add Tenant Isolation Tests

## When to Use

- An API endpoint has a tenant-scoped path: `/api/tenants/{tenantId}/...`
- The entity table has a `tenant_id` column (listed in `TenantScopedTables.java`)
- The existing `*ApiTest.java` does NOT have a `TenantIsolation` nested class

## Prerequisites

- The API test class extends `IntegrationTest`
- `TenantIsolationTestHelper` is available (`@Autowired`)
- The entity has tenant-scoped CRUD endpoints

## Procedure

### Step 1 — Identify the API and Required Capabilities

1. Open the API controller (e.g., `ScenarioApi.java`, `TenantGroupApi.java`)
2. Find the `@AccessControl` annotations on CRUD methods
3. Note the `resourceType` and `actionPerformed` values
4. Map to capabilities using `Capability.of(resourceType, action)`:
   - CREATE → `MANAGE_*` capability
   - READ → `ACCESS_*` capability
   - SEARCH → `ACCESS_*` capability (search endpoints are open for grantable resources)
   - WRITE → `MANAGE_*` capability
   - DELETE → `DELETE_*` capability

### Step 2 — Add TenantIsolationTestHelper to the Test Class

```java
@Autowired private TenantIsolationTestHelper tenantIsolationHelper;
```
We also need the `EntityManager` to populate with pre-requisites data.
```java
@Autowired private jakarta.persistence.EntityManager entityManager;
```

Also add imports:
```java
import io.openaev.utils.TenantIsolationTestHelper;
import jakarta.persistence.EntityManager;
```

### Step 3 — Create the Nested `TenantIsolation` Class

Add a `@Nested` class at the end of the test class with `@WithMockUser` (bare — no
capabilities via annotation; real capabilities come from DB via `createTenantWithCapabilities`).

```java
@Nested
@DisplayName("Tenant Isolation")
@WithMockUser
class TenantIsolation {
  // tests go here
}
```

### Step 4 — Evict Hibernate L1 Cache Between Create and Cross-Tenant Access

**Critical**: When a test creates an entity and then reads it within the same `@Transactional`
test, Hibernate's L1 (session) cache may return the entity directly **without hitting the
database**. Since RLS filtering happens at the PostgreSQL level, a cached `findById()` bypasses
RLS entirely — making the test pass even when isolation is broken.

**Always call `entityManager.flush()` + `entityManager.clear()` between the create and the
cross-tenant access** to force Hibernate to issue a real SQL query that goes through RLS.

> NOTE: this is done in switchToTenant() for example.
```java
public void switchToTenant(String tenantId, EntityManager entityManager) {
  entityManager.flush();
  entityManager.clear();
  ...
}
```
### Step 5 — Implement Test Methods

> **Template**: All 5 test templates (READ, same-tenant READ, SEARCH, UPDATE, DELETE) are in
> [`examples/tenant-isolation-templates.md`](examples/tenant-isolation-templates.md).
> Read that file for the full code templates with placeholder documentation.

> **Real-world examples** (read these for patterns to follow):
> - [`AssetGroupApiTest.TenantIsolation`](../../../openaev-api/src/test/java/io/openaev/rest/asset_group/AssetGroupApiTest.java) — fixture + REST API create
> - [`AtomicTestingApiTest.TenantIsolation`](../../../openaev-api/src/test/java/io/openaev/rest/AtomicTestingApiTest.java) — composer + `switchToTenant()`
> - [`ExerciseApiTest.TenantIsolation`](../../../openaev-api/src/test/java/io/openaev/rest/exercise/ExerciseApiTest.java) — REST API create

**Key rules:**

1. **Use Fixture classes** for input DTOs: `{EntityFixture}.createDefault{CreateInput}("name")`
   instead of inline `new DTO()` + setters. Add the fixture method if it doesn't exist.

2. **Use Composers** when creating entities directly (bypassing REST API):
   `injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get()`
   combined with `tenantIsolationHelper.switchToTenant()`.

3. **Expected status codes** — always use deterministic `assertEquals`:
   - **404** (most common): Hibernate `@Filter` blocks `findById()` → `ElementNotFoundException`

4. **Flush + clear** before cross-tenant access (see Step 4).

