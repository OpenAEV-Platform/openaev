package io.openaev.migration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Seeds a self-contained demo dataset for the Findings feature (POC branch poc/FindingPage), so
 * environments freshly deployed from this branch (e.g. the feature-branch staging deploy) have data
 * to exercise Also Detected On, Raw response, Remediation, the finding_type filter, and the
 * credential test/verify feature without needing to be populated by hand first.
 *
 * <p>See {@code finding_demo_seed.sql} for the full contents and idempotency notes. Every INSERT
 * there uses {@code ON CONFLICT DO NOTHING} on the row's primary key, so this migration is a no-op
 * if run again against a database that already has this exact demo data (e.g. a local dev DB that
 * already had it seeded manually with the same fixed IDs).
 */
@Component
public class V6_20260910093000000__Add_finding_demo_seed_data extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    ClassPathResource classPathResource = new ClassPathResource("finding_demo_seed.sql");
    String seedSql;
    try (InputStream inputStream = classPathResource.getInputStream()) {
      seedSql = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(seedSql);
    }
  }
}
