package io.openaev.migration;

import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Remembers, per payload, the version for which its users kept the injects on older versions, and
 * indexes the injector contract of step templates, read to find the injects left on an older
 * version. {@code steps} grows with use: its index is built concurrently, outside a transaction.
 */
@Component
public class V6_20261123000000000__Payload_identity_older_versions_kept extends BaseJavaMigration {

  private static final String STEP_CONTRACT_INDEX = "idx_steps_template_injector_contract";

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE payload_identities
            ADD COLUMN IF NOT EXISTS payload_identity_older_versions_kept_for VARCHAR(255)
              REFERENCES payloads (payload_id) ON DELETE SET NULL;
          """);
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS payload_identities_older_versions_kept_for_idx
            ON payload_identities (payload_identity_older_versions_kept_for);
          """);
      if (isInvalid(statement)) {
        statement.execute("DROP INDEX CONCURRENTLY IF EXISTS " + STEP_CONTRACT_INDEX);
      }
      statement.execute(
          "CREATE INDEX CONCURRENTLY IF NOT EXISTS "
              + STEP_CONTRACT_INDEX
              + """
               ON steps ((CASE jsonb_typeof(step_data -> 'inject_injector_contract')
                WHEN 'object' THEN step_data -> 'inject_injector_contract' ->> 'injector_contract_id'
                WHEN 'string' THEN step_data ->> 'inject_injector_contract'
              END))
              WHERE step_status = 'TEMPLATE' AND step_template_id IS NULL;
              """);
    }
  }

  /** An index left by an interrupted build is invalid. */
  private static boolean isInvalid(Statement statement) throws Exception {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT NOT indisvalid FROM pg_index WHERE indexrelid = to_regclass('"
                + STEP_CONTRACT_INDEX
                + "')")) {
      return result.next() && result.getBoolean(1);
    }
  }
}
