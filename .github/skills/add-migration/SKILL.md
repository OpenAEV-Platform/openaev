---
name: add-migration
description: >-
  Creates a Flyway Java-based migration for schema changes. Handles table creation,
  column additions, tenant isolation, and ES reindex. Use when asked to modify the database schema.
---

# Add Migration

## Prerequisites

- What schema change is needed (new table, new column, FK, index, etc.)
- Whether the table is tenant-scoped or platform-level
- The next migration version number

**Before writing the migration**, confirm with the user:
- The exact DDL statements to execute
- Whether the change is additive (new table/column) or destructive (drop/rename)
- How tenant-scoped data will be handled (FK to `tenants`, `NOT NULL` vs nullable `tenant_id`)

## Procedure

### Step 1 — Pick the version

```bash
ls openaev-api/src/main/java/io/openaev/migration/ | sort | tail -5
```

Pattern: `V{major}_{yyyyMMddHHmmssSSS}__{description}.java` — 17-digit timestamp (not a sequential
number), snake_case description, timestamp after the latest existing migration. Migrations below
`V6_` use the legacy `V{major}_{NN}__PascalCase` scheme — do not extend it.

### Step 2 — Create the migration class

Location: `openaev-api/src/main/java/io/openaev/migration/`

```java
package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260812100000000__Description extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // SQL here
    }
  }
}
```

### Step 3 — Apply tenant isolation (if applicable)

For tenant-scoped tables:
```sql
CREATE TABLE my_entities (
  my_entity_id VARCHAR(255) NOT NULL,
  my_entity_name VARCHAR(255) NOT NULL,
  tenant_id VARCHAR(255) NOT NULL,
  -- ... other columns ...
  CONSTRAINT pk_my_entities PRIMARY KEY (my_entity_id),
  CONSTRAINT fk_my_entities_tenant FOREIGN KEY (tenant_id)
    REFERENCES tenants(tenant_id) ON DELETE CASCADE
);
CREATE INDEX idx_my_entities_tenant ON my_entities(tenant_id);
```

Default tenant: `2cffad3a-0001-4078-b0e2-ef74274022c3`

### Step 4 — Handle ES reindex (if needed)

If modifying an entity indexed in Elasticsearch, add a reindex trigger:
```sql
DELETE FROM indexing_status;
```

### Step 5 — Verify

```bash
mvn clean install -DskipTests -Pdev    # Migration runs on startup
mvn test                                # Ensure tests pass with new schema
```

