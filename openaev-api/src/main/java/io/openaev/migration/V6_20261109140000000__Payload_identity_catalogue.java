package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261109140000000__Payload_identity_catalogue extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE payload_identities
            ADD COLUMN IF NOT EXISTS payload_identity_repository VARCHAR(255),
            ADD COLUMN IF NOT EXISTS payload_identity_latest_version VARCHAR(255)
              REFERENCES payloads (payload_id) ON DELETE SET NULL,
            DROP CONSTRAINT IF EXISTS payload_identities_external_id_tenant_unique;
          """);
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS payload_identities_latest_version_idx
            ON payload_identities (payload_identity_latest_version);
          """);
      // One local identity per external ID, and one per catalogue repository and external ID.
      statement.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS payload_identities_local_external_id_unique
            ON payload_identities (tenant_id, payload_external_id)
            WHERE payload_identity_repository IS NULL;
          """);
      statement.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS payload_identities_catalogue_external_id_unique
            ON payload_identities (tenant_id, payload_identity_repository, payload_external_id)
            WHERE payload_identity_repository IS NOT NULL;
          """);
    }
  }
}
