package io.openaev.migration;

import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261106120000000__Payload_identities extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS payload_identities (
            payload_identity_id VARCHAR(255) NOT NULL PRIMARY KEY,
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
            payload_external_id VARCHAR(255),
            payload_source VARCHAR(255) NOT NULL,
            payload_collector_type VARCHAR(255),
            payload_identity_created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            payload_identity_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT payload_identities_collector_type_fk FOREIGN KEY (payload_collector_type)
              REFERENCES collector_types (collector_type_id),
            CONSTRAINT payload_identities_external_id_tenant_unique
              UNIQUE (payload_external_id, tenant_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_payload_identities_tenant"
              + " ON payload_identities (tenant_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_payload_identities_collector_type"
              + " ON payload_identities (payload_collector_type);");
      statement.execute(
          """
          ALTER TABLE payloads
            ADD COLUMN IF NOT EXISTS payload_identity VARCHAR(255),
            ADD COLUMN IF NOT EXISTS payload_version VARCHAR(255);
          """);
      if (hasLegacyColumns(statement)) {
        // Every existing payload becomes its own identity, at the first version.
        statement.execute(
            """
            INSERT INTO payload_identities (payload_identity_id, tenant_id, payload_external_id,
              payload_source, payload_collector_type, payload_identity_created_at, payload_identity_updated_at)
            SELECT payload_id, tenant_id, payload_external_id,
              COALESCE(payload_source, 'MANUAL'), payload_collector_type,
              payload_created_at, payload_updated_at
            FROM payloads
            ON CONFLICT (payload_identity_id) DO NOTHING;
            """);
        statement.execute(
            "UPDATE payloads SET payload_identity = payload_id, payload_version = '1.0.0'"
                + " WHERE payload_identity IS NULL;");
        statement.execute(
            """
            ALTER TABLE payloads
              DROP CONSTRAINT IF EXISTS payloads_tenant_unique,
              DROP COLUMN IF EXISTS payload_external_id,
              DROP COLUMN IF EXISTS payload_source,
              DROP COLUMN IF EXISTS payload_collector_type;
            """);
      }
      statement.execute(
          """
          ALTER TABLE payloads
            ALTER COLUMN payload_identity SET NOT NULL,
            ALTER COLUMN payload_version SET NOT NULL;
          """);
      statement.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payloads_identity_fk') THEN
              ALTER TABLE payloads
                ADD CONSTRAINT payloads_identity_fk FOREIGN KEY (payload_identity)
                  REFERENCES payload_identities (payload_identity_id) ON DELETE CASCADE;
            END IF;
            IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payloads_identity_version_unique') THEN
              ALTER TABLE payloads
                ADD CONSTRAINT payloads_identity_version_unique UNIQUE (payload_identity, payload_version);
            END IF;
          END $$;
          """);
    }
  }

  private static boolean hasLegacyColumns(Statement statement) throws Exception {
    try (ResultSet rows =
        statement.executeQuery(
            "SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()"
                + " AND table_name = 'payloads' AND column_name = 'payload_external_id'")) {
      return rows.next();
    }
  }
}
