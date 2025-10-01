package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_63__Add_agent_to_inject_expectation extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Add agent to inject expectation
    select.execute(
        """
        ALTER TABLE injects_expectations ADD COLUMN agent_id varchar(256) constraint fk_agent references agents on delete cascade;
        """);
    select.execute(
        "CREATE INDEX IF NOT EXISTS idx_inject_expectation_agent_id ON injects_expectations(agent_id);");
  }
}
