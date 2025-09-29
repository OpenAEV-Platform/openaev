package io.openbas.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_36__Force_es_reindex_inject extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // re-index inject in ES
      statement.executeUpdate("DELETE FROM indexing_status WHERE indexing_status_type = 'inject';");
    }
  }
}
