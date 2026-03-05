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
- Whether tenant-scoped or platform-level
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

### Step 5 — Create DTOs + Mapper

Location: `openaev-api/src/main/java/io/openaev/api/{feature}/`

- `{Entity}Input` and `{Entity}Output` as Java `record`
- `{Entity}Mapper` with static `fromInput()` + `toOutput()`

### Step 6 — Create the Controller

Location: `openaev-api/src/main/java/io/openaev/api/{feature}/`

- `@AccessControl` + `@LogExecutionTime` + `@Operation` on every endpoint
- CRUD + search endpoints

### Step 7 — Create the Migration

Location: `openaev-api/src/main/java/io/openaev/migration/`

- Find next version number in existing migrations
- `CREATE TABLE`, FK constraints, indexes

### Step 8 — Create Test Fixtures + Composer

Location: `openaev-api/src/test/java/io/openaev/utils/fixtures/`

- Fixture: `createDefault{Entity}()` with random names
- Composer: extends `ComposerBase`, inner `Composer` class

### Step 9 — Create Integration Test

Location: `openaev-api/src/test/java/io/openaev/rest/` or `api/`

- `@Nested @DisplayName` groups, `@WithMockUser`, `assertThatJson`

### Step 10 — Create Frontend Actions + Page

Location: `openaev-front/src/actions/{feature}/` and `src/admin/components/`

- `{feature}-action.ts`, `{feature}-helper.d.ts`, `{feature}-schema.ts`
- List page, form, queryable, hooks

### Step 11 — Verify

```bash
mvn spotless:apply
mvn test
cd openaev-front && yarn lint && yarn check-ts && yarn test
```

