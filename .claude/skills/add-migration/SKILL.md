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

### Step 1 — Find the next version number

```bash
ls openaev-api/src/main/java/io/openaev/migration/ | sort | tail -5
```

Pattern: `V4_{XX}__Description.java` — increment `XX`.

### Step 2 — Create the migration class

Location: `openaev-api/src/main/java/io/openaev/migration/`

```java
package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_XX__Description extends BaseJavaMigration {
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

### Step 4 — Rebuild the search index (if needed)

Needed when the migration changes data an indexed model reads, or how its documents are built. The
model name is the `index` of its `@Indexable` class (`grep -rn "@Indexable(index" openaev-model/src/main/java`),
and it is also its `indexing_status_type`. A new field on an indexed model needs no migration: the
engine drivers add it to the existing index at boot.

**Never `DELETE FROM indexing_status`.** During a rolling deploy, a pod still running re-creates the
deleted row before the new pod's boot check, which then finds a row and never wipes the index.

**Re-feed in place** — the documents only need rewriting. The index stays complete throughout. Example:
`V6_20261022140626000__Request_finding_refeed_after_sensitive_value_masking`.
```java
try (Statement statement = context.getConnection().createStatement()) {
  statement.executeUpdate(
      "UPDATE indexing_status SET indexing_status_indexing_date = '" + Instant.EPOCH
          + "', indexing_status_last_id = NULL WHERE indexing_status_type = '" + MODEL
          + "' AND indexing_status_indexing_date < '" + EsIndexingUtils.REINDEX_REQUESTED_THRESHOLD + "'");
}
// Re-feeds once more after engine.indexing-refeed-reassert-delay-seconds, over what pods
// still running the previous version wrote meanwhile
EsIndexingUtils.requestRefeedAtStartup(MODEL);
```

**Reset** — stale documents must disappear (rows deleted by raw SQL, changed ids). The index is wiped
and rebuilt, so its dashboards are incomplete until the rebuild ends. Example:
`V6_20260924120000000__Request_expectation_inject_reindex_after_group_parent_dedup`.
```java
try (Statement statement = context.getConnection().createStatement()) {
  statement.execute(
      "INSERT INTO indexing_status (indexing_status_type, indexing_status_indexing_date) VALUES ('"
          + MODEL + "', '" + EsIndexingUtils.REINDEX_REQUESTED_CURSOR + "')"
          + " ON CONFLICT (indexing_status_type)"
          + " DO UPDATE SET indexing_status_indexing_date = EXCLUDED.indexing_status_indexing_date");
}
EsIndexingUtils.requestReindexAtStartup(MODEL);
```

### Step 5 — Verify

```bash
mvn clean install -DskipTests -Pdev    # Migration runs on startup
mvn test                                # Ensure tests pass with new schema
```

