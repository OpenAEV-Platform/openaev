package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260818100000000__Add_status_to_agents extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE agents ADD COLUMN IF NOT EXISTS agent_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';");
      statement.execute(
          "UPDATE agents SET agent_status = 'INACTIVE'"
              + " WHERE agent_last_seen IS NULL"
              + " OR agent_last_seen < NOW() - INTERVAL '1 hour';");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_agents_status_last_seen"
              + " ON agents(agent_status, agent_last_seen);");
    }
  }
}
