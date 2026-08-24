package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Flags findings whose value holds sensitive material, so the API can redact them.
 *
 * <p>The column defaults to {@code false}: only the finding types declared sensitive by their
 * output processor are flagged. Credentials findings are the only ones today - their value is
 * {@code username:password} or {@code username:hash} - so existing rows of that type are backfilled
 * to keep already detected credentials masked, not only the ones detected after the upgrade.
 *
 * <p>The stored value stays cleartext (deduplication, correlation and attack paths rely on it); the
 * redaction happens at serialization time.
 */
@Component
public class V6_20260824180000000__Add_finding_is_sensitive extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE findings
          ADD COLUMN IF NOT EXISTS finding_is_sensitive BOOLEAN NOT NULL DEFAULT FALSE;
          """);

      statement.execute(
          """
          UPDATE findings
          SET finding_is_sensitive = TRUE
          WHERE finding_type = 'Credentials'
            AND finding_is_sensitive IS FALSE;
          """);
    }
  }
}
