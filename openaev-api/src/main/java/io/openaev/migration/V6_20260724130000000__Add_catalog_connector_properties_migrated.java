package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds a persistent one-shot marker for the legacy properties-to-instance migration on catalog
 * connectors. Previously the only "already migrated" marker was the migrated connector instance
 * itself: deleting that instance re-armed the migration, which re-created the instance on every
 * restart (deleted executors kept coming back).
 *
 * <p>Backfill: connectors that already have a PROPERTIES_MIGRATION instance have demonstrably run
 * the migration, so they are marked migrated. Connectors without one keep {@code false} and get
 * exactly one (now enable-gated) migration pass on next startup.
 */
@Component
public class V6_20260724130000000__Add_catalog_connector_properties_migrated
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              ALTER TABLE catalog_connectors
              ADD COLUMN IF NOT EXISTS catalog_connector_properties_migrated BOOLEAN NOT NULL DEFAULT false
              """);
      statement.execute(
          """
              UPDATE catalog_connectors
              SET catalog_connector_properties_migrated = true
              WHERE catalog_connector_id IN (
                SELECT DISTINCT connector_instance_catalog_id
                FROM connector_instances
                WHERE connector_instance_source = 'PROPERTIES_MIGRATION'
              )
              """);
    }
  }
}

// -- ROLLBACK --
// ALTER TABLE catalog_connectors DROP COLUMN IF EXISTS catalog_connector_properties_migrated;
