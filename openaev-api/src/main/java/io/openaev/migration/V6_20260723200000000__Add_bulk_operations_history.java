package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code bulk_operations} journal backing the permanent massive-operations indicator.
 *
 * <p>The top bar indicator shows the massive (bulk) operations of the current user with their live
 * progress, and keeps a per-user history of the most recent operations (capped in code). Journaling
 * in PostgreSQL makes the history survive restarts and lets any node serve it; live progress events
 * still flow through the SSE stream. Rows carry the launching user id (history is per user, never
 * shared) and the tenant id.
 *
 * <p>Idempotent and lock-light (new table only).
 */
@Component
public class V6_20260723200000000__Add_bulk_operations_history extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS bulk_operations (
            operation_id VARCHAR(255) NOT NULL,
            operation_action VARCHAR(255) NOT NULL,
            operation_entity VARCHAR(255) NOT NULL,
            operation_total INT NOT NULL,
            operation_processed INT NOT NULL DEFAULT 0,
            operation_status VARCHAR(32) NOT NULL,
            operation_started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            operation_finished_at TIMESTAMP WITH TIME ZONE,
            operation_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            operation_tenant_id VARCHAR(255),
            operation_user_id VARCHAR(255),
            CONSTRAINT pk_bulk_operations PRIMARY KEY (operation_id)
          );
          """);
      // Idempotency helper for environments that created the table before the updated_at
      // column landed (same unreleased branch): Java migrations carry no checksum, so the
      // ALTER keeps them consistent without a repair.
      statement.execute(
          "ALTER TABLE bulk_operations ADD COLUMN IF NOT EXISTS operation_updated_at"
              + " TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_bulk_operations_user"
              + " ON bulk_operations (operation_user_id, operation_started_at DESC);");
    }
  }
}
