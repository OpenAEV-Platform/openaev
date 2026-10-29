---
applyTo: "openaev-api/src/main/java/io/openaev/migration/**"
description: "Database migration conventions: Flyway Java-based migrations, naming, tenant isolation, safety rules"
---

# Database Migration Conventions

## Migration Framework

OpenAEV uses **Java-based Flyway migrations** (not SQL files).

## Naming Convention

```
V{Y}_{XX}__{Description}.java
```

- `V{Y}_` is the major version prefix
- `{XX}` is a sequential number — **check the latest migration before creating a new one**:
  ```bash
  ls openaev-api/src/main/java/io/openaev/migration/ | sort | tail -5
  ```
- `{Description}` uses PascalCase, descriptive of the change
- Example: `V4_57__AddTenantToAlerts.java`

## Migration Class Structure

```java
package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_57__AddTenantToAlerts extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            // SQL here
            statement.execute("ALTER TABLE alerts ADD COLUMN tenant_id VARCHAR(255)");
        }
    }
}
```

## Critical Rules

1. **Migrations must be idempotent** — use `IF NOT EXISTS`, `IF EXISTS` guards
2. **Never modify an existing migration** — create a new one instead
3. **Never drop columns or tables** without a deprecation migration first
4. **Always add default values** for new `NOT NULL` columns on existing tables
5. **Large data migrations** should be batched (1000 rows at a time) to avoid lock contention
6. **Search index rebuild**: if the migration changes data an indexed model reads, or how its documents are built, request a rebuild of that model (its `indexing_status_type` is the `index` of its `@Indexable` class) — never with `DELETE FROM indexing_status`. A pod still running during a rolling deploy re-creates the deleted row before the new pod's boot check, so the wipe silently never happens. Pick one of the two patterns of `EsIndexingUtils`:
   - **Re-feed in place** — the documents only need rewriting: move the cursor back to epoch (`UPDATE indexing_status SET indexing_status_indexing_date = Instant.EPOCH, indexing_status_last_id = NULL WHERE indexing_status_type = '<model>' AND indexing_status_indexing_date < EsIndexingUtils.REINDEX_REQUESTED_THRESHOLD`) and call `EsIndexingUtils.requestRefeedAtStartup("<model>")`. The index stays complete throughout. Example: `V6_20261022140626000__Request_finding_refeed_after_sensitive_value_masking`.
   - **Reset** — stale documents must disappear (rows deleted by raw SQL, changed ids): upsert the `EsIndexingUtils.REINDEX_REQUESTED_CURSOR` sentinel and call `EsIndexingUtils.requestReindexAtStartup("<model>")`. The index is wiped and rebuilt, so its dashboards are incomplete until the rebuild ends. Example: `V6_20260924120000000__Request_expectation_inject_reindex_after_group_parent_dedup`.

   A new field on an indexed model needs no migration: the engine drivers add it to the existing index at boot. Re-feed only if the existing documents must carry its value.

## Anti-Patterns

- ❌ SQL files instead of Java migrations — project uses Java-based Flyway
- ❌ `DROP TABLE` without `IF EXISTS`
- ❌ `ALTER TABLE ADD COLUMN ... NOT NULL` without `DEFAULT` on existing populated table
- ❌ Single unique constraint on tenant-scoped entity (must include `tenant_id`)
- ❌ Missing `ON DELETE CASCADE` on tenant FK — orphan rows after tenant deletion
- ❌ Modifying an existing migration file — Flyway checksums will break
- ❌ `DELETE FROM indexing_status` to rebuild a search index — lost on a rolling deploy; re-feed in place or reset with the sentinel instead (rule 6)

## Verification

```bash
mvn clean install -DskipTests -Pdev    # Migration runs on startup
mvn test                                # Ensure tests pass with new schema
```

