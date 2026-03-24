---
applyTo: "openaev-model/src/main/java/**/model/**,openaev-model/src/main/java/**/repository/**,**/migration/**,**/application.sql"
description: "Database conventions: schema naming, Flyway migrations, PostgreSQL, tenant isolation"
---

# Database Conventions

## Schema Naming

- Table: `snake_case_plural` (e.g. `platform_groups`)
- Column: `{entity_singular}_{field}` (e.g. `group_name`)
- ID column: `{entity_singular}_id`
- FK to tenant: `tenant_id VARCHAR(255) NOT NULL` + `ON DELETE CASCADE`
- Always add index on `tenant_id` for tenant-scoped tables
- Join tables: `{table1}_{table2}` with composite PK + FKs `ON DELETE CASCADE`

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

- Java-based: `V4_{next}__Description.java` in `io.openaev.migration`
- Find next number: `ls openaev-api/src/main/java/io/openaev/migration/ | sort | tail -5`
- Extends `BaseJavaMigration`, annotated `@Component`
- Use `context.getConnection().createStatement()` for raw SQL
- Batch: `statement.addBatch(...)` then `statement.executeBatch()`
- Default tenant UUID: `2cffad3a-0001-4078-b0e2-ef74274022c3`
- For ES reindex: add a migration that deletes from `indexing_status`

## Multi-tenancy

- `TenantContext.getCurrentTenant()` — ThreadLocal
- `@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")` on all `TenantBase` entities
- Activated automatically by `HibernateFilterTransactionAspect` on every `@Transactional`
- `TenantBaseListener`: auto-sets tenant on `@PrePersist`, asserts immutability on `@PreUpdate`
- Native `@Query` bypasses the filter — always add `WHERE tenant_id = :tenantId`
- User is platform-level (no tenant filter), Group/Role are tenant-scoped

## Audit Timestamps

- Entities with `created_at` / `updated_at` must implement `Auditable` and register `AuditableListener`
- Do **not** use Hibernate-specific `@CreationTimestamp` / `@UpdateTimestamp`
- `AuditableListener` auto-sets `createdAt` on `@PrePersist` and refreshes `updatedAt` on `@PreUpdate`
- Mark `created_at` column with `updatable = false`
