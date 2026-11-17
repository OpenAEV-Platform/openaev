package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261116100000000__Payload_revocations_by_catalogue extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE payload_revocations ADD COLUMN IF NOT EXISTS"
              + " payload_revocation_by_catalogue BOOLEAN NOT NULL DEFAULT false;");
    }
  }
}
