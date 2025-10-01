package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_12__List_Widget_config_update extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {

      // migrate the JSON configuration for config type 'list
      // transform the 'series' array into a 'perspective' object
      // the value of 'perspective' is the first item in the 'series' array
      // then drop the 'series' array
      statement.execute(
          """
            UPDATE widgets
            SET widget_config = widget_config || CONCAT('{"perspective":', (widget_config ->> 'series')::jsonb ->> 0, '}')::jsonb
            WHERE widget_config @> '{"widget_configuration_type": "list"}';

            UPDATE widgets
            SET widget_config = widget_config - 'series'
            WHERE widget_config @> '{"widget_configuration_type": "list"}';
            """);
    }
  }
}
