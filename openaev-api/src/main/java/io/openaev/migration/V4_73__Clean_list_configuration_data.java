package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_73__Clean_list_configuration_data extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
            UPDATE widgets
            SET widget_config = widget_config - 'series'  -- JSONB operator: removes key
            WHERE widget_type = 'LIST'
              AND widget_config ? 'series'; \s
        """);
    }
  }
}
