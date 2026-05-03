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
6. **ES reindex**: if modifying an entity indexed in Elasticsearch, add `DELETE FROM indexing_status`

## Anti-Patterns

- ❌ SQL files instead of Java migrations — project uses Java-based Flyway
- ❌ `DROP TABLE` without `IF EXISTS`
- ❌ `ALTER TABLE ADD COLUMN ... NOT NULL` without `DEFAULT` on existing populated table
- ❌ Single unique constraint on tenant-scoped entity (must include `tenant_id`)
- ❌ Missing `ON DELETE CASCADE` on tenant FK — orphan rows after tenant deletion
- ❌ Modifying an existing migration file — Flyway checksums will break

## Verification

```bash
mvn clean install -DskipTests -Pdev    # Migration runs on startup
mvn test                                # Ensure tests pass with new schema
```

