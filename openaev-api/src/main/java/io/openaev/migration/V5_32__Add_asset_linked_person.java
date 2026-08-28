package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the optional {@code asset_linked_person} column on the {@code assets} table: for IDENTITY
 * assets it links the identity to the physical person (a {@code users} row) it belongs to. A
 * database FK with {@code ON DELETE SET NULL} keeps referential integrity without blocking user
 * deletion. Additive and nullable - existing rows are unaffected.
 */
@Component
public class V5_32__Add_asset_linked_person extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS asset_linked_person varchar(255);");
      statement.execute(
          "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname ="
              + " 'fk_assets_linked_person' AND conrelid = 'assets'::regclass) THEN ALTER TABLE"
              + " assets ADD CONSTRAINT fk_assets_linked_person FOREIGN KEY (asset_linked_person)"
              + " REFERENCES users (user_id) ON DELETE SET NULL; END IF; END $$;");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_tenant_linked_person"
              + " ON assets (tenant_id, asset_linked_person);");
    }
  }
}
