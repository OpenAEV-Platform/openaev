You are adding a new JPA entity to OpenAEV.

> Follow conventions from `backend.instructions.md`, `database.instructions.md`, and `testing.instructions.md`.

## Steps

1. **Entity class** in `openaev-model/src/main/java/io/openaev/database/model/`
    - Follow `Group.java` (tenant-scoped) or `Tenant.java` (platform-level)
    - `@ControlledUuidGeneration`, `@Queryable`, `@CreationTimestamp`/`@UpdateTimestamp`
    - Implement `isUserHasAccess(User user)` + `equals/hashCode`

2. **Repository** in `openaev-model/src/main/java/io/openaev/database/repository/`
    - `JpaRepository<Entity, String>` + `JpaSpecificationExecutor<Entity>`

3. **ResourceType** + **Capability** — add enum values with parent hierarchy

4. **Service** in `openaev-api/src/main/java/io/openaev/service/`

5. **DTOs + Mapper** in `openaev-api/src/main/java/io/openaev/api/{feature}/`

6. **Controller** in same package — CRUD + search

7. **Migration** in `openaev-api/src/main/java/io/openaev/migration/`

8. **Tests** — Fixture, Composer, integration test, unit test



