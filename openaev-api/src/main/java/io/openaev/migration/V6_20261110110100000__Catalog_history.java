package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261110110100000__Catalog_history extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS catalog_history (
            catalog_history_id VARCHAR(255) NOT NULL PRIMARY KEY,
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
            catalog_history_type VARCHAR(255) NOT NULL,
            catalog_history_repository VARCHAR(255) NOT NULL,
            catalog_history_external_id VARCHAR(255) NOT NULL,
            catalog_history_version VARCHAR(255) NOT NULL,
            catalog_history_action VARCHAR(255) NOT NULL,
            catalog_history_user VARCHAR(255) REFERENCES users (user_id) ON DELETE SET NULL,
            catalog_history_created_at TIMESTAMPTZ NOT NULL DEFAULT now()
          );
          """);
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_catalog_history_content
            ON catalog_history (tenant_id, catalog_history_type, catalog_history_repository,
              catalog_history_external_id, catalog_history_created_at DESC);
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_catalog_history_user"
              + " ON catalog_history (catalog_history_user);");
    }
  }
}
