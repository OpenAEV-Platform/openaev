package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_57__Convert_expectations_to_jsonb extends BaseJavaMigration {

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      // Convert column to jsonb if not already
      select.execute(
          """
              DO $$
              BEGIN
                  IF EXISTS (
                      SELECT 1 FROM information_schema.columns
                      WHERE table_name = 'injects_expectations'
                      AND column_name = 'inject_expectation_signatures'
                      AND data_type != 'jsonb'
                  ) THEN
                      ALTER TABLE injects_expectations
                          ALTER COLUMN inject_expectation_signatures
                          TYPE jsonb
                          USING inject_expectation_signatures::jsonb;
                  END IF;
              END $$;
              """);

      // Drop constraints if they exist and recreate them as deferrable
      select.execute(
          """
              DO $$
              BEGIN
                  IF EXISTS (
                      SELECT 1 FROM information_schema.table_constraints
                      WHERE constraint_name = 'execution_traces_execution_agent_id_fkey'
                      AND table_name = 'execution_traces'
                  ) THEN
                      ALTER TABLE execution_traces DROP CONSTRAINT execution_traces_execution_agent_id_fkey;
                  END IF;

                  IF EXISTS (
                      SELECT 1 FROM information_schema.table_constraints
                      WHERE constraint_name = 'execution_traces_execution_inject_status_id_fkey'
                      AND table_name = 'execution_traces'
                  ) THEN
                      ALTER TABLE execution_traces DROP CONSTRAINT execution_traces_execution_inject_status_id_fkey;
                  END IF;

                  IF EXISTS (
                      SELECT 1 FROM information_schema.table_constraints
                      WHERE constraint_name = 'execution_traces_execution_inject_test_status_id_fkey'
                      AND table_name = 'execution_traces'
                  ) THEN
                      ALTER TABLE execution_traces DROP CONSTRAINT execution_traces_execution_inject_test_status_id_fkey;
                  END IF;
              END $$;
              """);

      // Add constraints if they don't exist
      select.execute(
          """
              DO $$
              BEGIN
                  IF NOT EXISTS (
                      SELECT 1 FROM information_schema.table_constraints
                      WHERE constraint_name = 'execution_traces_execution_inject_status_id_fkey'
                      AND table_name = 'execution_traces'
                  ) THEN
                      ALTER TABLE execution_traces
                          ADD CONSTRAINT execution_traces_execution_inject_status_id_fkey
                          FOREIGN KEY (execution_inject_status_id)
                          REFERENCES injects_statuses(status_id)
                          ON DELETE CASCADE
                          DEFERRABLE INITIALLY DEFERRED;
                  END IF;

                  IF NOT EXISTS (
                      SELECT 1 FROM information_schema.table_constraints
                      WHERE constraint_name = 'execution_traces_execution_inject_test_status_id_fkey'
                      AND table_name = 'execution_traces'
                  ) THEN
                      ALTER TABLE execution_traces
                          ADD CONSTRAINT execution_traces_execution_inject_test_status_id_fkey
                          FOREIGN KEY (execution_inject_test_status_id)
                          REFERENCES injects_tests_statuses(status_id)
                          ON DELETE CASCADE
                          DEFERRABLE INITIALLY DEFERRED;
                  END IF;

                  IF NOT EXISTS (
                      SELECT 1 FROM information_schema.table_constraints
                      WHERE constraint_name = 'execution_traces_execution_agent_id_fkey'
                      AND table_name = 'execution_traces'
                  ) THEN
                      ALTER TABLE execution_traces
                          ADD CONSTRAINT execution_traces_execution_agent_id_fkey
                          FOREIGN KEY (execution_agent_id)
                          REFERENCES agents(agent_id)
                          ON DELETE CASCADE
                          DEFERRABLE INITIALLY DEFERRED;
                  END IF;
              END $$;
              """);

      select.execute(
          """
              CREATE INDEX IF NOT EXISTS idx_injects_expectations_inject_agent
                  ON injects_expectations(inject_id, agent_id);
              """);
    }
  }
}
