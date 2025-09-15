package io.openbas.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_09__Add_unique_constraint_name_security_platform extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // 1. Extract security platforms uniques by name and type
      stmt.execute(
          """
          CREATE TEMP TABLE temp_security_platform_mapping AS
          SELECT asset_id AS old_id,
                 MIN(asset_id) OVER (PARTITION BY asset_name, asset_type) AS new_id
          FROM assets
          WHERE asset_type = 'SecurityPlatform';
      """);

      // 2. Update assets_tags
      stmt.execute(
          """
          UPDATE assets_tags at
          SET asset_id = m.new_id
          FROM temp_security_platform_mapping m
          WHERE at.asset_id = m.old_id
            AND m.old_id != m.new_id;
      """);

      // 3. Update injects_expectations_traces
      stmt.execute(
          """
          UPDATE injects_expectations_traces t
          SET inject_expectation_trace_source_id = m.new_id
          FROM temp_security_platform_mapping m
          WHERE t.inject_expectation_trace_source_id = m.old_id
            AND m.old_id != m.new_id;
      """);

      // 4. Update collectors
      stmt.execute(
          """
          UPDATE collectors c
          SET collector_security_platform = m.new_id
          FROM temp_security_platform_mapping m
          WHERE c.collector_security_platform = m.old_id
            AND m.old_id != m.new_id;
      """);

      // 5. Delete duplicate SecurityPlatform assets
      stmt.execute(
          """
          DELETE FROM assets
          WHERE asset_id IN (
              SELECT old_id
              FROM temp_security_platform_mapping
              WHERE old_id != new_id
          );
      """);

      // 6. Add unique constraint on (asset_name, asset_type) where type = 'SecurityPlatform'
      stmt.execute(
          """
          CREATE UNIQUE INDEX unique_security_platform_name_type_idx
          ON assets (asset_name, asset_type)
          WHERE asset_type = 'SecurityPlatform';
      """);

      // 7. Clean up
      stmt.execute("DROP TABLE temp_security_platform_mapping;");
    }
  }
}
