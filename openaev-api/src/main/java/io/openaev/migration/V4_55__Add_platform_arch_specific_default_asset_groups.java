package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_55__Add_platform_arch_specific_default_asset_groups extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE security_coverages
          ADD COLUMN security_coverage_platforms_affinity TEXT[];
          """);

      statement.execute(
          """
          ALTER TABLE tag_rules
          ADD COLUMN tag_rule_protected BOOLEAN NOT NULL DEFAULT FALSE;

          -- update the only known immutable tag so far
          WITH protected AS (
              SELECT tag_id as id, CASE WHEN tag_name = 'opencti' THEN TRUE ELSE FALSE END AS status
              FROM tags
          )
          UPDATE tag_rules SET tag_rule_protected = protected.status
          FROM protected
          WHERE tag_id = protected.id;
          """);

      statement.execute(
          """
          CREATE TABLE datapacks(
              datapack_id VARCHAR(255) PRIMARY KEY
          );
          """);
    }
  }
}
