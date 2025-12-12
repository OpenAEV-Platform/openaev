package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_52__Add_coverage_bundle_hash_md5_column extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
                ALTER TABLE security_coverages
                ADD COLUMN security_coverage_bundle_hash_md5 VARCHAR(32);
                """);

      statement.execute(
          """
                UPDATE security_coverages
                SET security_coverage_bundle_hash_md5 = LOWER(md5(security_coverage_content::text))
                WHERE security_coverage_content IS NOT NULL;
                """);

      statement.execute(
          """
                ALTER TABLE security_coverages
                ALTER COLUMN security_coverage_bundle_hash_md5 SET NOT NULL;
                """);

      statement.execute(
          """
                CREATE UNIQUE INDEX idx_security_coverage_bundle_hash_md5
                ON security_coverages(security_coverage_bundle_hash_md5);
                """);
    }
  }
}
