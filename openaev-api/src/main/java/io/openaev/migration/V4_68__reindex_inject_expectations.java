package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_68__reindex_inject_expectations extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // re-index inject in ES
      statement.executeUpdate(
          "DELETE FROM indexing_status WHERE indexing_status_type = 'expectation-inject';");
    }
  }
}
