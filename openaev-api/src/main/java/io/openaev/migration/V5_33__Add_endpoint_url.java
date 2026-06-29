package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code endpoint_url} column on the {@code assets} table. URL-based asset categories (web
 * applications, cloud endpoints, ...) store their target URL here; it is null for agent-managed
 * hosts. Additive and nullable.
 */
@Component
public class V5_33__Add_endpoint_url extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE assets ADD COLUMN IF NOT EXISTS endpoint_url text;");
    }
  }
}
