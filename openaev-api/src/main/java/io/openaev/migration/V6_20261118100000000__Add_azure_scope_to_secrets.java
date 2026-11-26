package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the optional Azure scope that tells which resource a token is requested for.
 *
 * <p>The column is nullable and has no default: a credential without it keeps requesting a token
 * for the resource manager of its Azure cloud. It is shared by both Azure subtypes, like {@code
 * secret_azure_authority_host_uri}.
 */
@Component
public class V6_20261118100000000__Add_azure_scope_to_secrets extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
            ALTER TABLE secrets
            ADD COLUMN IF NOT EXISTS secret_azure_scope VARCHAR(255)
          """);
    }
  }
}
