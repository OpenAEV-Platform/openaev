package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_42__Update_filters_empty_value extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
                   update asset_groups set asset_group_dynamic_filter = '{"mode":"or","filters":[{"key":"endpoint_platform","mode":"or","values":[],"operator":"not_empty"}]}'
                """);
    }
  }
}
