package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260719070000000__Add_payload_author extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Polymorphic author for payloads: a payload is authored by a user, a team
      // OR an organization (mutually exclusive nullable FKs). Nullable + ON DELETE
      // SET NULL so removing the actor never blocks or cascades onto the payload.
      statement.executeUpdate(
          """
          ALTER TABLE payloads
            ADD COLUMN IF NOT EXISTS payload_author_user VARCHAR(255)
              REFERENCES users(user_id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS payload_author_team VARCHAR(255)
              REFERENCES teams(team_id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS payload_author_organization VARCHAR(255)
              REFERENCES organizations(organization_id) ON DELETE SET NULL;
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payloads_author_user ON payloads(payload_author_user);");
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payloads_author_team ON payloads(payload_author_team);");
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payloads_author_organization ON payloads(payload_author_organization);");
    }
  }
}
