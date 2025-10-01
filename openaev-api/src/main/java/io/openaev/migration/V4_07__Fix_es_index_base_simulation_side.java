package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_07__Fix_es_index_base_simulation_side extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // re-index findings in ES
      statement.executeUpdate(
          "DELETE FROM indexing_status WHERE indexing_status_type = 'finding';");
    }
  }
}
