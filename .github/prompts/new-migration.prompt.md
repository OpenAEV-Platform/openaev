You are writing a Flyway migration for OpenAEV.

## Conventions

- **File**: `openaev-api/src/main/java/io/openaev/migration/V4_XX__Description.java`
- **Naming**: `V4_{next_number}__Snake_case_description` (double underscore after version)
- Find the next number by looking at existing migrations in the `migration` package.

## Template

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
      statement.execute("...");
    }
  }
}
```

## Rules

- Use `statement.addBatch(...)` + `statement.executeBatch()` for multiple statements
- Tenant-scoped tables: add `tenant_id VARCHAR(255) NOT NULL` FK to `tenants(tenant_id) ON DELETE CASCADE` + index
- Default tenant UUID: `2cffad3a-0001-4078-b0e2-ef74274022c3` (use `Tenant.DEFAULT_TENANT_UUID`)
- Join tables: composite primary key + FKs with `ON DELETE CASCADE`
- ⚠️ Native SQL bypasses Hibernate tenant filter — always include `WHERE tenant_id = ...`

