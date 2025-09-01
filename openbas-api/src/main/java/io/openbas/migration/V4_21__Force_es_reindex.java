package io.openbas.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_21__Force_es_reindex extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // re-index in ES
      statement.executeUpdate(
          "DELETE FROM indexing_status WHERE indexing_status_type in ('expectation-inject', 'simulation', 'scenario', 'endpoint', 'inject', 'vulnerable-endpoint', 'finding', 'security-platform', 'tag');");
    }
  }
}
