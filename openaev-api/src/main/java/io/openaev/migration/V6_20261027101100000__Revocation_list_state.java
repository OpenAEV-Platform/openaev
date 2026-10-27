package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * The last revocation list received from each content repository: its serial (a newer list wins, an
 * older one is refused), when it was issued and downloaded, and when it expires.
 */
@Component
public class V6_20261027101100000__Revocation_list_state extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS revocation_list_states (
            revocation_list_repository VARCHAR(255) NOT NULL PRIMARY KEY,
            revocation_list_serial BIGINT NOT NULL,
            revocation_list_issued_at TIMESTAMP WITH TIME ZONE,
            revocation_list_expires_at TIMESTAMP WITH TIME ZONE,
            revocation_list_downloaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
          );
          """);
    }
  }
}
