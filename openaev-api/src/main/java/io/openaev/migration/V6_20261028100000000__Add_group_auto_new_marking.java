package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Lets a group receive every marking created in its tenant, as in OpenCTI. */
@Component
public class V6_20261028100000000__Add_group_auto_new_marking extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE groups ADD COLUMN IF NOT EXISTS group_auto_new_marking BOOLEAN NOT NULL"
              + " DEFAULT false");
    }
  }
}
