package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_88__Enforce_constraint_asset_group_dynamic_filter extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE asset_groups ALTER COLUMN asset_group_dynamic_filter SET DEFAULT '{"mode":"and","filters":[]}';
          UPDATE asset_groups SET asset_group_dynamic_filter = '{"mode":"and","filters":[]}' where asset_group_dynamic_filter IS NULL;
          ALTER TABLE asset_groups ALTER COLUMN asset_group_dynamic_filter SET NOT NULL;
          """);
    }
  }
}
