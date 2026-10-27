package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Payload variants (ADR-011): the fields a tenant changed on a catalogue payload, the release it
 * conflicts with, and the lift of a revocation a variant version inherits from its base.
 */
@Component
public class V6_20261021010000000__Payload_variants extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE payload_identities
            ADD COLUMN IF NOT EXISTS overrides JSONB,
            ADD COLUMN IF NOT EXISTS conflict_target_payload_id VARCHAR(255)
              REFERENCES payloads (payload_id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;
          """);
      statement.execute(
          """
          ALTER TABLE content_revocations
            ADD COLUMN IF NOT EXISTS content_revocation_lifts VARCHAR(255)
              REFERENCES content_revocations (content_revocation_id) ON DELETE CASCADE;
          """);
      // A lift row names the variant version it frees: it is not a revocation of that version.
      statement.execute("DROP INDEX IF EXISTS content_revocations_payload_active_unique;");
      statement.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS content_revocations_payload_active_unique
            ON content_revocations (content_revocation_payload)
            WHERE content_revocation_lifted_at IS NULL AND content_revocation_lifts IS NULL;
          """);
      statement.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS content_revocations_inheritance_lift_unique
            ON content_revocations (content_revocation_payload, content_revocation_lifts)
            WHERE content_revocation_lifts IS NOT NULL;
          """);
    }
  }
}
