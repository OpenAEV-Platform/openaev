package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Repairs {@code payloads.payload_arguments} rows that were persisted as the Hypersistence
 * {@code JsonType} wrapper object ({@code {"type":"json","value":"[...]","null":false}}) instead of
 * a plain JSON array.
 *
 * <p>Such rows make the {@code Payload} entity fail to deserialize {@code List<PayloadArgument>}
 * (Jackson {@code MismatchedInputException}: START_OBJECT where an array is expected), which broke
 * {@code ManagerIntegrationsSyncJob} on startup and any read that lazily loads payloads (e.g. the
 * Assets page).
 *
 * <p>Why the earlier {@code V6_20260720178453597} unwrap did not catch it: that migration only
 * touched array-shaped {@code payload_arguments} (to strip {@code subtype}) and the
 * {@code status_payload_output} column; its {@code json_typeof(...) = 'array'} guard skipped the
 * object-wrapped shape, so those rows survived. This migration closes that gap.
 *
 * <p>Idempotent and re-runnable: only object-shaped rows carrying a {@code value} key are rewritten;
 * once unwrapped they are arrays and no longer match the guard, so a second run (or a fresh install
 * that never had the bad shape) is a no-op. Lock-light single-column DML on a small table.
 */
@Component
public class V6_20260720193000000__Repair_wrapped_json_payload_arguments
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // Unwrap the JsonType envelope: take the inner "value" (a JSON-array string) and re-parse it
      // back into the array the column is supposed to hold. Cast to jsonb only for the ->> and ?
      // operators; the column itself stays json (matching the entity mapping).
      stmt.execute(
          """
          UPDATE payloads
          SET payload_arguments = ((payload_arguments::jsonb) ->> 'value')::json
          WHERE payload_arguments IS NOT NULL
            AND json_typeof(payload_arguments) = 'object'
            AND (payload_arguments::jsonb) ? 'value';
          """);
    }
  }
}
