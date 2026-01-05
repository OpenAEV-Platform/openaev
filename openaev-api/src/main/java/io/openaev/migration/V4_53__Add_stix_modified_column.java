package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Statement;

@Component
public class V4_53__Add_stix_modified_column extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
                ALTER TABLE security_coverages
                ADD COLUMN security_coverage_stix_modified TIMESTAMPTZ;
                """);

      statement.execute(
          """
                UPDATE security_coverages
                SET security_coverage_stix_modified = security_coverage_updated_at
                WHERE security_coverage_stix_modified IS NULL;
                """);

      statement.execute(
          """
                CREATE UNIQUE INDEX idx_security_coverage_bundle_hash_md5
                ON security_coverages(security_coverage_bundle_hash_md5);
                """);
    }
  }
}
