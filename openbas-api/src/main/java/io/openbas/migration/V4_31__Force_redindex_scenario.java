package io.openbas.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_31__Force_redindex_scenario extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // re-index scenarios and inject-expectation
      statement.executeUpdate(
          "DELETE FROM indexing_status WHERE indexing_status_type in ( 'scenario', 'expectation-inject');");
    }
  }
}
