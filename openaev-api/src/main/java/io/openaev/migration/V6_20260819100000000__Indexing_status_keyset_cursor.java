package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the nullable {@code indexing_status_last_id} column to {@code indexing_status}.
 *
 * <p>The indexing cursor is a single timestamp today, so a batch whose LIMIT falls inside a group
 * of rows sharing one {@code updated_at} can skip the remainder permanently. This column carries
 * the second component of a keyset cursor {@code (updated_at, base_id)} for handlers that opt in.
 *
 * <p>No backfill and no cursor reset: a null {@code last_id} degrades to the existing
 * timestamp-only paging, so every model keeps its current position and behaviour.
 *
 * <p>Additive, idempotent, lock-light: a nullable {@code ADD COLUMN} is metadata-only on PostgreSQL
 * 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260819100000000__Indexing_status_keyset_cursor extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE indexing_status ADD COLUMN IF NOT EXISTS indexing_status_last_id varchar(255);");
    }
  }
}
