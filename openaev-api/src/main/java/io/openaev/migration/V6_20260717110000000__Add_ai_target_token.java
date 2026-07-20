package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code ai_target_token} column superseding {@code ai_target_api_key_variable}. The
 * credential used to reach an AI Target is now carried directly (and optionally) on the target
 * itself - set manually when creating the target or provisioned by a collector - rather than
 * resolved from an injector-side environment variable. The token is optional: some targets (e.g.
 * local model deployments) require no authentication.
 *
 * <p>The legacy {@code ai_target_api_key_variable} column is deliberately kept (unmapped and
 * unused) for one release cycle so a rollback to the previous version keeps working; a follow-up
 * migration will drop it.
 */
@Component
public class V6_20260717110000000__Add_ai_target_token extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE assets ADD COLUMN IF NOT EXISTS ai_target_token text;");
    }
  }
}
