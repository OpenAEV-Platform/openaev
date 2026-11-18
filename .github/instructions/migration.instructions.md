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
2. **Never modify an existing migration**: create a new one instead. The one exception (ADR-023): a
   released Java migration found dangerous (for example the WAL-fill shape of rule 5) may be
   rewritten in place, but only when the new version reaches the same final state from any
   state the old one can leave a platform in. Otherwise ship a new migration that finishes the
   job.
3. **Never drop columns or tables** without a deprecation migration first
4. **Always add default values** for new `NOT NULL` columns on existing tables
5. **Large-table data migrations** follow ADR-023.
   Batching alone is not enough: primary-key-order batches on a table with random (UUID) primary
   keys scatter each batch across the whole table and turn a periodic `VACUUM` into a near-full-table
   rescan (the cause of a production WAL-fill incident). Default to a physical walk by `ctid` page
   ranges, one committed transaction per range, no `VACUUM` in the migration, paced against WAL
   archiving/replica lag, index built `CONCURRENTLY`, constraints added `NOT VALID` then
   `VALIDATE CONSTRAINT`, every lock request under a short `lock_timeout` with retry, every step
   resumable after a crash. Reference implementation:
   `V6_20261112060000000__Add_tenant_id_to_execution_traces.java`. Use a background-job fill with a
   later `NOT NULL` migration instead only when the walk's startup delay is unacceptable for that
   table, and say why in the migration's Javadoc.
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
- ❌ Modifying an existing migration file, unless it is the ADR-023 in-place-fix exception of rule 2
- ❌ `DELETE FROM indexing_status` to rebuild a search index — lost on a rolling deploy; re-feed in place or reset with the sentinel instead (rule 6)
- ❌ Batching a large-table rewrite in primary key order when the primary key is a random UUID: scatters each batch across the whole table (rule 5, ADR-023)
- ❌ `VACUUM` called inside a migration: let autovacuum reclaim dead tuples on its own schedule
- ❌ A non-concurrent `CREATE INDEX` or an inline-validated constraint added on a large table after a backfill: use `CONCURRENTLY` and `NOT VALID` then `VALIDATE CONSTRAINT`
- ❌ A lock request (`ALTER TABLE`, `LOCK TABLE`) on a large table with no `lock_timeout` and retry: it queues behind another session and blocks every later query on the table
- ❌ A large-table rewrite in one uncommitted transaction with no pacing, especially across several tables whose locks then accumulate until one final commit

## Verification

```bash
mvn clean install -DskipTests -Pdev    # Migration runs on startup
mvn test                                # Ensure tests pass with new schema
```

