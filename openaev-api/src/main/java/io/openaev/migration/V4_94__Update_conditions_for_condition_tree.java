package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_94__Update_conditions_for_condition_tree extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // --- conditions table updates ---
      stmt.execute(
          "ALTER TABLE conditions ADD COLUMN IF NOT EXISTS condition_workflow_id VARCHAR(255);");
      stmt.execute(
          "ALTER TABLE conditions ADD COLUMN IF NOT EXISTS condition_key_type VARCHAR(255);");
      stmt.execute("ALTER TABLE conditions ADD COLUMN IF NOT EXISTS condition_name VARCHAR(255);");
      stmt.execute("ALTER TABLE conditions ADD COLUMN IF NOT EXISTS condition_description TEXT;");
      stmt.execute(
          "ALTER TABLE conditions ADD COLUMN IF NOT EXISTS condition_key_subtype VARCHAR(255);");
      stmt.execute("ALTER TABLE conditions ADD COLUMN IF NOT EXISTS condition_key VARCHAR(255);");
      stmt.execute(
          "ALTER TABLE steps ADD COLUMN IF NOT EXISTS step_condition_key_types VARCHAR(255);");

      // Legacy cleanup: drop the old conditions.step_id column if it still exists.
      stmt.execute(
          """
          DO $$
          BEGIN
            IF EXISTS (
              SELECT 1
              FROM information_schema.columns
              WHERE table_name = 'conditions'
                AND column_name = 'step_id'
            ) THEN
              ALTER TABLE conditions DROP COLUMN step_id;
            END IF;
          END $$;
          """);

      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_conditions_workflow_id ON conditions(condition_workflow_id);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_conditions_key_type ON conditions(condition_key_type);");

      // FK on condition_workflow_id → workflows(workflow_id) ON DELETE CASCADE
      // so deleting a workflow cascades to all its conditions (and transitively to
      // conditions_steps).
      stmt.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (
              SELECT 1 FROM pg_constraint
              WHERE conname = 'fk_conditions_workflow'
            ) THEN
              ALTER TABLE conditions
                ADD CONSTRAINT fk_conditions_workflow
                FOREIGN KEY (condition_workflow_id) REFERENCES workflows(workflow_id) ON DELETE CASCADE;
            END IF;
          END $$;
          """);

      // Fix step_from_id FK to use ON DELETE SET NULL so deleting a step nullifies the reference.
      stmt.execute(
          """
          DO $$
          BEGIN
            IF EXISTS (
              SELECT 1 FROM pg_constraint
              WHERE conname = 'conditions_step_from_id_fkey'
            ) THEN
              ALTER TABLE conditions DROP CONSTRAINT conditions_step_from_id_fkey;
            END IF;
          END $$;
          """);

      stmt.execute(
          """
          ALTER TABLE conditions
            ADD CONSTRAINT conditions_step_from_id_fkey
            FOREIGN KEY (step_from_id) REFERENCES steps(step_id) ON DELETE SET NULL;
          """);

      // conditions_steps link table
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS conditions_steps (
              condition_step_id VARCHAR(255) NOT NULL,
            condition_id VARCHAR(255) NOT NULL,
            step_id VARCHAR(255) NOT NULL,
            is_root BOOLEAN NOT NULL DEFAULT FALSE,
            PRIMARY KEY (condition_step_id),
            CONSTRAINT fk_conditions_steps_condition
              FOREIGN KEY (condition_id) REFERENCES conditions(condition_id) ON DELETE CASCADE,
            CONSTRAINT fk_conditions_steps_step
              FOREIGN KEY (step_id) REFERENCES steps(step_id) ON DELETE CASCADE
          );
          """);

      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_conditions_steps_step_id ON conditions_steps(step_id);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_conditions_steps_condition_id ON conditions_steps(condition_id);");
    }
  }
}
