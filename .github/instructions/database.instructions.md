---
applyTo: "openaev-model/src/main/java/**/model/**,openaev-model/src/main/java/**/repository/**,**/migration/**,**/application.sql"
description: "Database conventions: schema naming, Flyway migrations, PostgreSQL, tenant isolation"
---

# Database Conventions

## Schema Naming

- Table: `snake_case_plural` (e.g. `platform_groups`)
- Column: `{entity_singular}_{field}` (e.g. `group_name`)
- ID column: `{entity_singular}_id`
- FK to tenant: `tenant_id VARCHAR(255)` — `NOT NULL` for tenant-only entities, **`NULLABLE`** for dual-scope entities (Settings, User, Role, Group) + `ON DELETE CASCADE`
- Always add index on `tenant_id` for tenant-scoped and dual-scope tables
- Join tables: `{table1}_{table2}` with composite PK + FKs `ON DELETE CASCADE`
- **Every FK column must have a supporting index** (for composite FKs, index the same column set). PostgreSQL does not index FK columns automatically, and `ON DELETE CASCADE`/`SET NULL` checks run **once per deleted parent row** — an unindexed FK turns each parent delete into a full sequential scan of the child table (cause of the boot crash-loop in #6780)

## Business Keys & Unique Constraints

- Use `@BusinessId` annotation on fields that serve as natural/business keys (e.g. `tag_name`, `domain_external_id`, `attack_pattern_external_id`)
- **Single-tenant context**: `@Column(unique = true)` or `@Table(uniqueConstraints = ...)` is sufficient
- **Multi-tenant context**: unique constraints MUST be **tenant-scoped** — use composite unique constraints:
  ```sql
  ALTER TABLE tags ADD CONSTRAINT uk_tags_name_tenant UNIQUE (tag_name, tenant_id);
  ```
- Never use a global `unique = true` on a business key in a tenant-scoped entity — two tenants must be able to have the same tag name, domain, etc.
- When migrating existing `unique = true` to multi-tenancy: drop the old constraint, create a composite one with `tenant_id`

## Flyway Migrations

- Java-based, in `io.openaev.migration`; name: see [migration.instructions.md › Naming Convention](migration.instructions.md#naming-convention)
- Extends `BaseJavaMigration`, annotated `@Component`
- Use `context.getConnection().createStatement()` for raw SQL
- Batch a data change over 1000 rows: `statement.addBatch(...)` then `statement.executeBatch()`. On a large table, follow ADR-023 (`migration.instructions.md` rule 5), not a plain primary-key-order batch loop
- Default tenant UUID: `2cffad3a-0001-4078-b0e2-ef74274022c3`
- For a search index rebuild: re-feed in place or reset with the sentinel, never `DELETE FROM indexing_status` (see rule 6 of `migration.instructions.md`)

## Multi-tenancy

- `TenantContext.getCurrentTenant()` — ThreadLocal
- No Hibernate `@Filter` on any entity (`TenantFilterRemovedArchTest`): read isolation is the statement inspector, for the tables in `openaev.tenant.active-tables`
- `TenantBaseListener`: auto-sets tenant on `@PrePersist`, asserts immutability on `@PreUpdate`
- A query on a table outside `active-tables` has no ambient isolation: add `WHERE tenant_id = :tenantId`

### Dual-Scope Entities (Settings, User, Role, Group)

- Implement `DualScopeBase` — single table with **nullable `tenant_id`**: `NULL` = platform, non-null = tenant-scoped
- Repository keeps generic JPA methods (`findByKey()`, `findAll()`, etc.)
- Repository adds platform-scoped methods: `findByKeyAndTenantIsNull()`, `findAllByTenantIsNull()`
- Repository adds tenant-scoped methods: `findByKeyAndTenantId()`, `findAllByTenantId()`

## Audit Timestamps

- Entities with `created_at` / `updated_at` must implement `Auditable` and register `AuditableListener`
- Do **not** use Hibernate-specific `@CreationTimestamp` / `@UpdateTimestamp`
- `AuditableListener` auto-sets `createdAt` on `@PrePersist` and refreshes `updatedAt` on `@PreUpdate`
- Mark `created_at` column with `updatable = false`
