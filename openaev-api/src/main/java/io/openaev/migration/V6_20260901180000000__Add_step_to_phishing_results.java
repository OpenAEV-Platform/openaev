package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds a {@code phishing_result_step} FK so a chaining execution can create a {@code
 * PhishingResult} referencing the already-persisted {@code Step} instead of the not-yet-committed
 * {@code Inject} it is about to create. At least one of {@code phishing_result_step} / {@code
 * phishing_result_inject} must be set; both being set is allowed (the inject is backfilled once
 * committed, see {@code PhishingTrackingService#resolveByToken}). Either FK deletion cascades to
 * the result row, so no row is ever left pointing at a step or inject that no longer exists.
 */
@Component
public class V6_20260901180000000__Add_step_to_phishing_results extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE phishing_results
              ADD COLUMN IF NOT EXISTS phishing_result_step varchar(255)
                  CONSTRAINT phishing_results_step_fk
                      REFERENCES steps (step_id) ON DELETE CASCADE;
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_phishing_results_step "
              + "ON phishing_results (phishing_result_step)");
      statement.execute(
          """
          ALTER TABLE phishing_results
              ADD CONSTRAINT phishing_results_step_or_inject_chk
                  CHECK (phishing_result_step IS NOT NULL OR phishing_result_inject IS NOT NULL);
          """);
    }
  }
}
