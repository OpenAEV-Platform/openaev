package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260916173300000__Add_inject_authorisation extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              CREATE TABLE IF NOT EXISTS inject_authorisations (
                inject_authorisation_id VARCHAR(255) PRIMARY KEY,
                inject_id VARCHAR(255) NOT NULL UNIQUE,
                inject_authorisation_code VARCHAR(255) NOT NULL,
                inject_authorisation_issued_at TIMESTAMP WITH TIME ZONE,
                CONSTRAINT fk_inject_authorisations_inject_id
                  FOREIGN KEY (inject_id) REFERENCES injects (inject_id) ON DELETE CASCADE
              )
              """);

      statement.execute(
          """
              CREATE INDEX IF NOT EXISTS idx_inject_authorisations_inject_id
                ON inject_authorisations (inject_id)
              """);

      statement.execute(
          """
              CREATE TABLE IF NOT EXISTS injects_secret_references (
                inject_id VARCHAR(255) NOT NULL,
                secret_reference_id VARCHAR(255) NOT NULL,
                PRIMARY KEY (inject_id, secret_reference_id),
                CONSTRAINT fk_injects_secret_references_inject_id
                  FOREIGN KEY (inject_id) REFERENCES injects (inject_id) ON DELETE CASCADE,
                CONSTRAINT fk_injects_secret_references_id
                  FOREIGN KEY (secret_reference_id) REFERENCES secret_references (secret_reference_id) ON DELETE CASCADE
              )
              """);

      statement.execute(
          """
              CREATE INDEX IF NOT EXISTS idx_injects_secret_references_secret_reference_id
                ON injects_secret_references (secret_reference_id);
              """);
    }
  }
}
