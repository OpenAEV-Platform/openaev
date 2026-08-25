package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Flags findings whose value holds sensitive material, so the API can redact them. */
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
          WHERE finding_type IN ('Credentials', 'PasswordPolicy')
            AND finding_is_sensitive IS FALSE;
          """);
    }
  }
}
