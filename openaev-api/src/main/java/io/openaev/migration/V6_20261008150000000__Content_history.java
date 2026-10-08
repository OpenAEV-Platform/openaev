package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** History of catalogue content per tenant (ADR-011): installs, updates, rollbacks, copies. */
@Component
public class V6_20261008150000000__Content_history extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS content_history (
            content_history_id VARCHAR(255) NOT NULL PRIMARY KEY,
            content_history_type VARCHAR(255) NOT NULL,
            content_history_external_id VARCHAR(255),
            content_history_version VARCHAR(255),
            content_history_from_version VARCHAR(255),
            content_history_action VARCHAR(255) NOT NULL,
            content_history_automatic BOOLEAN NOT NULL DEFAULT false,
            content_history_entity_id VARCHAR(255) NOT NULL,
            content_history_user VARCHAR(255) REFERENCES users (user_id) ON DELETE SET NULL,
            content_history_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            content_history_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_content_history_tenant_id ON content_history (tenant_id);");
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_content_history_item
            ON content_history (tenant_id, content_history_type, content_history_external_id,
              content_history_created_at DESC);
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_content_history_user ON content_history (content_history_user);");
    }
  }
}
