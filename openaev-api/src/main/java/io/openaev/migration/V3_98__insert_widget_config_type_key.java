package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_98__insert_widget_config_type_key extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      String widgetConfigType =
          """
              UPDATE widgets
              SET widget_config = widget_config || '{"widget_configuration_type": "structural-histogram"}'
              WHERE widget_config @> '{"mode": "structural"}';

              UPDATE widgets
              SET widget_config = widget_config || '{"widget_configuration_type": "temporal-histogram"}'
              WHERE widget_config @> '{"mode": "temporal"}';
              """;

      statement.executeUpdate(widgetConfigType);
    }
  }
}
