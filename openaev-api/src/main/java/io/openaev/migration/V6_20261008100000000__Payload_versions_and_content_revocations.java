package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Versioned payloads (ADR-011): version columns, new uniqueness and platform-level revocations. */
@Component
public class V6_20261008100000000__Payload_versions_and_content_revocations
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE payloads
            ADD COLUMN IF NOT EXISTS payload_version VARCHAR(255),
            ADD COLUMN IF NOT EXISTS payload_content_hash VARCHAR(64),
            ADD COLUMN IF NOT EXISTS payload_origin_id VARCHAR(255)
              REFERENCES payloads (payload_id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS payload_is_latest BOOLEAN NOT NULL DEFAULT true;
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_payloads_origin_id ON payloads (payload_origin_id);");

      // One row per (external id, version, tenant); still one unversioned row per external id.
      statement.execute("ALTER TABLE payloads DROP CONSTRAINT IF EXISTS payloads_tenant_unique;");
      statement.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS payloads_external_id_version_tenant_unique
            ON payloads (payload_external_id, payload_version, tenant_id)
            WHERE payload_version IS NOT NULL;
          """);
      statement.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS payloads_external_id_unversioned_tenant_unique
            ON payloads (payload_external_id, tenant_id)
            WHERE payload_version IS NULL;
          """);
      statement.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS payloads_external_id_latest_tenant_unique
            ON payloads (payload_external_id, tenant_id)
            WHERE payload_is_latest;
          """);

      statement.execute(
          "ALTER TABLE injects ADD COLUMN IF NOT EXISTS inject_catalog_id VARCHAR(255);");

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS content_revocations (
            content_revocation_id VARCHAR(255) NOT NULL PRIMARY KEY,
            content_revocation_type VARCHAR(255) NOT NULL,
            content_revocation_external_id VARCHAR(255) NOT NULL,
            content_revocation_version VARCHAR(255) NOT NULL,
            content_revocation_reason TEXT,
            content_revocation_list_serial BIGINT NOT NULL,
            content_revocation_published_at TIMESTAMP WITH TIME ZONE,
            content_revocation_lifted_at TIMESTAMP WITH TIME ZONE,
            content_revocation_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            content_revocation_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
          );
          """);
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_content_revocations_active
            ON content_revocations (content_revocation_type, content_revocation_external_id)
            WHERE content_revocation_lifted_at IS NULL;
          """);
    }
  }
}
