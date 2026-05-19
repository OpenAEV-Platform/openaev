---
name: create-feature-module
description: >-
  Scaffolds a complete feature end-to-end: JPA entity, repository, service,
  DTOs, mapper, controller, migration, tests (fixture + composer + integration test),
  and frontend actions/page. Use when asked to create a new feature or module.
---

# Create Feature Module

## Prerequisites

- Entity name (singular, e.g. `PlatformGroup`)
- Table name (plural snake_case, e.g. `platform_groups`)
- Tenancy scope: **tenant-scoped**, **platform-level**, or **dual-scope**
- Fields with types and constraints

## Procedure

### Step 1 — Create the JPA Entity

Location: `openaev-model/src/main/java/io/openaev/database/model/`

Follow `Group.java` (tenant-scoped) or `Tenant.java` (platform-level):
- `@ControlledUuidGeneration` for ID
- `@Queryable` on filterable fields
- `@Transient @JsonIgnore ResourceType` field
- Collections initialized as mutable (`new ArrayList<>()`)
- Follow conventions from `database.instructions.md`

**If dual-scope** (Settings, User, Role, Group pattern):
- Implement `DualScopeBase` interface
- Use `ModelBaseListener` only (no `TenantBaseListener`)
- Do **NOT** add `@Filter("tenantFilter")`
- `tenant_id` must be **nullable**: `@JoinColumn(name = "tenant_id", nullable = true)`
- `@JsonIgnore` on the tenant relation

### Step 2 — Create the Repository

Location: `openaev-model/src/main/java/io/openaev/database/repository/`

```java
public interface {Entity}Repository extends JpaRepository<{Entity}, String>,
    JpaSpecificationExecutor<{Entity}> {}
```

### Step 3 — Add ResourceType + Capabilities

- Add value in `ResourceType.java`
- Add `ACCESS_`, `MANAGE_`, `DELETE_` in `Capability.java` with parent hierarchy

### Step 4 — Create the Service

Location: `openaev-api/src/main/java/io/openaev/service/`

- `@Service @RequiredArgsConstructor @Transactional(rollbackFor = Exception.class)`
- CRUD + search with pagination
- JavaDoc on all public methods

**If dual-scope — create TWO services:**
- `Platform{Entity}Service` — all queries use `findByTenantIsNull()` variants, never receives `tenantId`
- `Tenant{Entity}Service` — all queries use `findByTenantId(tenantId)` variants, receives `tenantId` as argument
- See `multi-tenancy.instructions.md` → Dual-Scope Entities for full pattern

### Step 5 — Create DTOs + Mapper

Location: `openaev-api/src/main/java/io/openaev/api/{feature}/`

- `{Entity}Input` and `{Entity}Output` as Java `record`
- `{Entity}Mapper` with static `fromInput()` + `toOutput()`

### Step 6 — Create the Controller

Location: `openaev-api/src/main/java/io/openaev/api/{feature}/`

- `@AccessControl` + `@LogExecutionTime` + `@Operation` on every endpoint
- CRUD + search endpoints
- **All new tenant-scoped APIs use `TENANT_PREFIX`**: `@RequestMapping(TENANT_PREFIX + "/{entities}")` → resolves to `/api/tenants/{tenantId}/{entities}`

**If dual-scope — create TWO controllers:**
- `Platform{Entity}Api` at `/api/platform-{entities}` — uses `Platform{Entity}Service`, platform-admin `@AccessControl`
- `Tenant{Entity}Api` at `TENANT_PREFIX + "/{entities}"` — tenant ID extracted from URL path, passed to `Tenant{Entity}Service`

### Step 7 — Create the Migration

Location: `openaev-api/src/main/java/io/openaev/migration/`

- Find next version number in existing migrations
- `CREATE TABLE`, FK constraints, indexes

**If dual-scope:**
- `tenant_id VARCHAR(255)` — **nullable**, FK to `tenants(tenant_id) ON DELETE CASCADE`
- Partial unique indexes:
  ```sql
  CREATE UNIQUE INDEX uk_{table}_name_platform ON {table} ({field}) WHERE tenant_id IS NULL;
  CREATE UNIQUE INDEX uk_{table}_name_tenant ON {table} ({field}, tenant_id) WHERE tenant_id IS NOT NULL;
  ```

### Step 8 — Create Test Fixtures + Composer

Location: `openaev-api/src/test/java/io/openaev/utils/fixtures/`

- Fixture: `createDefault{Entity}()` with random names
- Composer: extends `ComposerBase`, inner `Composer` class

**If dual-scope:**
- Fixture must support both: `createDefaultPlatform{Entity}()` (tenant = null) and `createDefaultTenant{Entity}(String tenantId)`

### Step 9 — Create Integration Test

Location: `openaev-api/src/test/java/io/openaev/api/{feature}/`

- `@Nested @DisplayName` groups, `@WithMockUser`, `assertThatJson`

**If dual-scope — add isolation tests:**
- `given_platformEntity_should_notAppearInTenantList`
- `given_tenantEntity_should_notAppearInPlatformList`
- `given_tenantA_should_notSeeTenantBEntities`
- Test both `Platform{Entity}Api` and `Tenant{Entity}Api` independently

### Step 10 — Create Frontend Actions + Page

> Follow templates and conventions from [frontend.instructions.md](../../instructions/frontend.instructions.md).

Location: `openaev-front/src/actions/{feature}/` and `src/admin/components/`

- `{feature}-action.ts` — API calls (CRUD + search)
- `{feature}-helper.d.ts` — TypeScript types (or use auto-generated `api-types.d.ts`)
- `{feature}-schema.ts` — Zod validation schema
- List page with `Queryable` + `DataTable`
- Create/Edit form with React Hook Form + Zod
- Permission guards with CASL (`ability.can(ACTIONS.MANAGE, SUBJECTS.X)`)

### Step 11 — Verify

```bash
mvn spotless:apply
mvn test
cd openaev-front && yarn lint && yarn check-ts && yarn test
```
