package io.openaev.migration;

import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Payload identities (ADR-018): what all versions of a payload share moves off the version rows,
 * with the pointer to the default version and the uninstall flag (F26). Every payload has a
 * version.
 */
@Component
public class V6_20261027100700000__Payload_identities extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      assertFreshSchema(statement);
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS payload_identities (
            identity_id VARCHAR(255) NOT NULL PRIMARY KEY,
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
            payload_repository VARCHAR(255),
            payload_external_id VARCHAR(255),
            payload_source VARCHAR(255) NOT NULL,
            payload_collector_type VARCHAR(255)
              REFERENCES collector_types (collector_type_id) ON DELETE SET NULL,
            managed BOOLEAN NOT NULL DEFAULT false,
            latest_payload_id VARCHAR(255),
            uninstalled BOOLEAN NOT NULL DEFAULT false,
            identity_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            identity_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            CONSTRAINT payload_identities_managed_check
              CHECK (managed = (payload_repository IS NOT NULL)),
            -- NULL is distinct: only managed identities are unique, local ones may claim an ID.
            CONSTRAINT payload_identities_repository_external_id_tenant_unique
              UNIQUE (payload_repository, payload_external_id, tenant_id),
            CONSTRAINT payload_identities_id_tenant_unique UNIQUE (identity_id, tenant_id)
          );
          """);
      // The legacy collector upsert targets one local identity per collector and external ID.
      statement.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS payload_identities_collector_external_id_tenant_unique
            ON payload_identities (payload_collector_type, payload_external_id, tenant_id)
            WHERE NOT managed AND payload_collector_type IS NOT NULL;
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_payload_identities_tenant ON payload_identities (tenant_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_payload_identities_external_id"
              + " ON payload_identities (payload_external_id);");

      // Every existing row is a local payload of its own: never merge rows into a managed identity.
      statement.execute(
          """
          INSERT INTO payload_identities (identity_id, tenant_id, payload_repository,
            payload_external_id, payload_source, payload_collector_type, managed,
            latest_payload_id, uninstalled, identity_created_at, identity_updated_at)
          SELECT payload_id, tenant_id, NULL, payload_external_id,
            COALESCE(payload_source, 'MANUAL'), payload_collector_type, false, payload_id, false,
            payload_created_at, payload_updated_at
          FROM payloads;
          """);
      statement.execute("ALTER TABLE payloads ADD COLUMN IF NOT EXISTS identity_id VARCHAR(255);");
      statement.execute("UPDATE payloads SET identity_id = payload_id, payload_version = '1.0.0';");
      statement.execute(
          """
          ALTER TABLE payloads
            ALTER COLUMN identity_id SET NOT NULL,
            ALTER COLUMN payload_version SET NOT NULL;
          """);

      statement.execute(
          """
          ALTER TABLE payloads
            ADD CONSTRAINT payloads_id_identity_unique UNIQUE (payload_id, identity_id),
            ADD CONSTRAINT payloads_identity_version_unique UNIQUE (identity_id, payload_version),
            ADD CONSTRAINT payloads_identity_tenant_fk FOREIGN KEY (identity_id, tenant_id)
              REFERENCES payload_identities (identity_id, tenant_id) ON DELETE CASCADE;
          """);
      // Deferred: moving a row to another identity updates both sides in one transaction.
      // Deleting the latest version leaves no pointer until the next refresh.
      statement.execute(
          """
          ALTER TABLE payload_identities
            ADD CONSTRAINT payload_identities_latest_fk FOREIGN KEY (latest_payload_id, identity_id)
              REFERENCES payloads (payload_id, identity_id) ON DELETE SET NULL (latest_payload_id)
              DEFERRABLE INITIALLY DEFERRED;
          """);

      statement.execute(
          """
          ALTER TABLE payloads
            DROP COLUMN IF EXISTS payload_external_id,
            DROP COLUMN IF EXISTS payload_source,
            DROP COLUMN IF EXISTS payload_collector_type;
          """);
    }
  }

  // Java migrations have no checksum: a database that ran an earlier draft would keep its schema.
  private static void assertFreshSchema(Statement statement) throws Exception {
    try (ResultSet rows =
        statement.executeQuery(
            "SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()"
                + " AND table_name = 'payloads' AND column_name = 'payload_is_latest'")) {
      if (rows.next()) {
        throw new IllegalStateException(
            "Database to reset: it ran an earlier draft of the ADR-018 migrations");
      }
    }
    try (ResultSet rows =
        statement.executeQuery("SELECT COUNT(*) FROM payloads WHERE payload_version IS NOT NULL")) {
      rows.next();
      if (rows.getLong(1) > 0) {
        throw new IllegalStateException(
            "Database to reset: payloads already have versions before their identities exist");
      }
    }
  }
}
