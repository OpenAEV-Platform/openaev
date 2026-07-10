---
name: review-migration
description: >-
  Step-by-step Flyway migration review for OpenAEV pull requests.
  Covers naming, class structure, idempotency, tenant isolation, data safety,
  and Elasticsearch reindex requirements.
---

# Review Migration

## Step 1 — Identify migrations in this PR

```bash
git diff --name-only HEAD~1 | grep "migration"
```

If no migration files are changed: skip this skill entirely.
If migrations are present: review each one following the steps below.

## Step 2 — Verify naming format and uniqueness

```bash
# List the last 10 migrations to verify naming format consistency
ls openaev-api/src/main/java/io/openaev/migration/ | sort | tail -10
```

Verify for each new migration:
- ☐ Name follows `V{major}_{yyyyMMddHHmmssSSS}__{description}.java`
- ☐ `yyyyMMddHHmmssSSS` timestamp block is present (17 digits)
- ☐ `{description}` uses snake_case (letters, digits, underscores)
- ☐ Migration filename is unique
- ☐ No existing migration file was modified (Flyway checksums will break)

## Step 3 — Verify class structure

```bash
cat openaev-api/src/main/java/io/openaev/migration/V{major}_{yyyyMMddHHmmssSSS}__*.java
```

Verify:
- ☐ `@Component` annotation present
- ☐ `extends BaseJavaMigration`
- ☐ `migrate(Context context)` method implemented
- ☐ Uses `try (Statement statement = context.getConnection().createStatement())` pattern
- ☐ No Spring beans injected via `@Autowired` — Flyway runs before Spring context is fully ready

## Step 4 — Verify idempotency

Every DDL statement must be guarded:

| Statement | Required guard |
|---|---|
| `CREATE TABLE` | `CREATE TABLE IF NOT EXISTS` |
| `ADD COLUMN` | `ADD COLUMN IF NOT EXISTS` |
| `DROP TABLE` | `DROP TABLE IF EXISTS` |
| `DROP COLUMN` | `DROP COLUMN IF EXISTS` |
| `CREATE INDEX` | `CREATE INDEX IF NOT EXISTS` |
| `CREATE UNIQUE INDEX` | `CREATE UNIQUE INDEX IF NOT EXISTS` |

```bash
# Check for unguarded DDL
grep -n "CREATE TABLE\|ADD COLUMN\|DROP TABLE\|DROP COLUMN\|CREATE INDEX" \
  openaev-api/src/main/java/io/openaev/migration/V{major}_{yyyyMMddHHmmssSSS}__*.java | grep -v "IF NOT EXISTS\|IF EXISTS"
```

Any result = 🟠 HIGH — not idempotent, will fail on re-run.

## Step 5 — Verify tenant isolation

```bash
# Check if new tables include tenant_id
grep -n "CREATE TABLE\|tenant_id\|REFERENCES tenants\|ON DELETE CASCADE" \
  openaev-api/src/main/java/io/openaev/migration/V{major}_{yyyyMMddHHmmssSSS}__*.java
```

Cross-reference with the entity class:
```bash
# Find the entity to determine if it extends TenantBase
grep -rn "extends TenantBase" openaev-model/src/main/java/ --include="*.java" | grep -i "{EntityName}"
```

For tenant-scoped tables, verify:
- ☐ `tenant_id VARCHAR(255) NOT NULL` column
- ☐ `REFERENCES tenants(tenant_id) ON DELETE CASCADE`
- ☐ `CREATE INDEX IF NOT EXISTS ... ON {table}(tenant_id)`
- ☐ Unique constraints are composite: `UNIQUE (field, tenant_id)` — never `UNIQUE (field)` alone

## Step 6 — Verify data safety

```bash
# Check for NOT NULL columns without DEFAULT
grep -n "NOT NULL" openaev-api/src/main/java/io/openaev/migration/V{major}_{yyyyMMddHHmmssSSS}__*.java | grep -v "DEFAULT\|tenant_id\|id"
```

For each `NOT NULL` column on an existing (non-new) table:
- ☐ A `DEFAULT` value is provided, OR
- ☐ The table is new (no existing rows) — document this assumption in the review

```bash
# Check for DROP statements
grep -n "DROP TABLE\|DROP COLUMN" openaev-api/src/main/java/io/openaev/migration/V{major}_{yyyyMMddHHmmssSSS}__*.java
```

Any `DROP` without a prior deprecation migration = 🔴 CRITICAL.

```bash
# Check for large data migrations (UPDATE/INSERT without WHERE or LIMIT)
grep -n "UPDATE\|INSERT INTO\|DELETE FROM" openaev-api/src/main/java/io/openaev/migration/V{major}_{yyyyMMddHHmmssSSS}__*.java
```

Large data migrations must be batched in chunks of 1000 rows.

## Step 7 — Verify Elasticsearch reindex

```bash
# Check if the migrated entity is indexed
grep -rn "indexing_status" openaev-api/src/main/java/io/openaev/migration/V{major}_{yyyyMMddHHmmssSSS}__*.java
```

If the migration modifies a table that has a corresponding Elasticsearch index:
- ☐ `DELETE FROM indexing_status WHERE indexing_status_type = '...'` is present to trigger reindex

To determine if an entity is indexed:
```bash
grep -rn "@Document\|@Indexed" openaev-model/src/main/java/ --include="*.java" | grep -i "{EntityName}"
```

## Step 8 — Compile findings

Generate the Migration Review Summary following the output format
defined in `migration-reviewer.agent.md`.

Determine Rollout Safety verdict:
- **SAFE ✅**: idempotent, no data risk, tenant isolation correct
- **CONDITIONAL ⚠️**: minor issues that can be fixed without blocking
- **UNSAFE 🔴**: any CRITICAL finding — PR must not merge
