package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261106120200000__Delete_payload_identity_with_its_last_version
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION delete_payload_identity_without_version() RETURNS TRIGGER AS $$
          BEGIN
            DELETE FROM payload_identities pi
            WHERE pi.payload_identity_id = OLD.payload_identity
              AND NOT EXISTS (
                SELECT 1 FROM payloads p WHERE p.payload_identity = OLD.payload_identity);
            RETURN OLD;
          END;
          $$ LANGUAGE plpgsql;

          CREATE OR REPLACE FUNCTION delete_grants_of_deleted_payload_identity() RETURNS TRIGGER AS $$
          BEGIN
            DELETE FROM grants
            WHERE grant_resource_type = 'THREAT_ARSENAL'
              AND grant_resource = OLD.payload_identity_id;
            RETURN OLD;
          END;
          $$ LANGUAGE plpgsql;

          DROP TRIGGER IF EXISTS trg_delete_payload_identity_without_version ON payloads;
          CREATE TRIGGER trg_delete_payload_identity_without_version
            AFTER DELETE ON payloads
            FOR EACH ROW EXECUTE FUNCTION delete_payload_identity_without_version();

          DROP TRIGGER IF EXISTS trg_delete_payload_identity_grants ON payload_identities;
          CREATE TRIGGER trg_delete_payload_identity_grants
            AFTER DELETE ON payload_identities
            FOR EACH ROW EXECUTE FUNCTION delete_grants_of_deleted_payload_identity();

          DELETE FROM payload_identities pi
          WHERE NOT EXISTS (SELECT 1 FROM payloads p WHERE p.payload_identity = pi.payload_identity_id);
          """);
    }
  }
}
