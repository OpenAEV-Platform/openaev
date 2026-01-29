package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_66__Delete_caldera_injector extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
        "UPDATE injectors SET injector_type = 'openaev_caldera_dummy' WHERE injector_type = 'openaev_caldera';");
    }
  }
}
