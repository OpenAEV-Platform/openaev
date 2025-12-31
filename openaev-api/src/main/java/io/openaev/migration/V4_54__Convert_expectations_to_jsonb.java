package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_54__Convert_expectations_to_jsonb extends BaseJavaMigration {

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
              ALTER TABLE injects_expectations
                  ALTER COLUMN inject_expectation_signatures
                  TYPE jsonb
                  USING inject_expectation_signatures::jsonb;
              -- Transform Foreign Key to deferrable key
              ALTER TABLE execution_traces
                DROP CONSTRAINT execution_traces_execution_agent_id_fkey,
                DROP CONSTRAINT execution_traces_execution_inject_status_id_fkey,
                DROP CONSTRAINT execution_traces_execution_inject_test_status_id_fkey;

              ALTER TABLE execution_traces
                ADD CONSTRAINT execution_traces_execution_inject_status_id_fkey
                FOREIGN KEY (execution_inject_status_id)
                REFERENCES injects_statuses(status_id)
                ON DELETE CASCADE
                DEFERRABLE INITIALLY DEFERRED,

                ADD CONSTRAINT execution_traces_execution_inject_test_status_id_fkey
                FOREIGN KEY (execution_inject_test_status_id)
                REFERENCES injects_tests_statuses(status_id)
                ON DELETE CASCADE
                DEFERRABLE INITIALLY DEFERRED,

                ADD CONSTRAINT execution_traces_execution_agent_id_fkey
                FOREIGN KEY (execution_agent_id)
                REFERENCES agents(agent_id)
                ON DELETE CASCADE
                DEFERRABLE INITIALLY DEFERRED;
              """);
      select.execute(
          """
              CREATE INDEX CONCURRENTLY idx_injects_expectations_inject_agent
                  ON injects_expectations(inject_id, agent_id);
              """);
    }
  }
}
