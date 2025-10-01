package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_94__tag_table_timestamps extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      String tagTableTimestamps =
          """
          ALTER TABLE tags
          ADD COLUMN tag_created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

          ALTER TABLE tags
          ADD COLUMN tag_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
          """;

      statement.executeUpdate(tagTableTimestamps);
    }
  }
}
