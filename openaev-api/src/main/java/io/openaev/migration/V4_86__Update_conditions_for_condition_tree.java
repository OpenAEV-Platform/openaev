package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_86__Update_conditions_for_condition_tree extends BaseJavaMigration {

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
      stmt.execute(
          """
          UPDATE conditions
          SET condition_key_type = condition_key
          WHERE condition_key_type IS NULL
            AND condition_key IS NOT NULL;
          """);
      stmt.execute("ALTER TABLE conditions DROP COLUMN IF EXISTS condition_key");

      // Legacy compatibility: if old conditions.step_id still exists, it must be nullable.
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
              ALTER TABLE conditions ALTER COLUMN step_id DROP NOT NULL;
            END IF;
          END $$;
          """);

      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_conditions_workflow_id ON conditions(condition_workflow_id);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_conditions_key_type ON conditions(condition_key_type);");

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

      // --- conditions_steps link table (merged from former V4_79 + V4_80) ---
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS conditions_steps (
            condition_id VARCHAR(255) NOT NULL,
            step_id VARCHAR(255) NOT NULL,
            is_root BOOLEAN NOT NULL DEFAULT FALSE,
            PRIMARY KEY (condition_id, step_id),
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

      // Surrogate key refactor on link table.
      stmt.execute(
          "ALTER TABLE conditions_steps ADD COLUMN IF NOT EXISTS condition_step_id VARCHAR(255);");

      stmt.execute(
          """
          UPDATE conditions_steps
          SET condition_step_id = CONCAT(condition_id, '_', step_id)
          WHERE condition_step_id IS NULL;
          """);

      stmt.execute(
          """
          DO $$
          BEGIN
            IF EXISTS (
              SELECT 1 FROM information_schema.table_constraints
              WHERE table_name = 'conditions_steps'
                AND constraint_type = 'PRIMARY KEY'
                AND constraint_name = 'conditions_steps_pkey'
            ) THEN
              ALTER TABLE conditions_steps DROP CONSTRAINT conditions_steps_pkey;
            END IF;
          END $$;
          """);

      stmt.execute("ALTER TABLE conditions_steps ALTER COLUMN condition_step_id SET NOT NULL;");

      stmt.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (
              SELECT 1 FROM information_schema.table_constraints
              WHERE table_name = 'conditions_steps'
                AND constraint_type = 'PRIMARY KEY'
                AND constraint_name = 'conditions_steps_pkey'
            ) THEN
              ALTER TABLE conditions_steps
                ADD CONSTRAINT conditions_steps_pkey PRIMARY KEY (condition_step_id);
            END IF;
          END $$;
          """);

      stmt.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (
              SELECT 1 FROM information_schema.table_constraints
              WHERE table_name = 'conditions_steps'
                AND constraint_type = 'UNIQUE'
                AND constraint_name = 'uk_conditions_steps_condition_step'
            ) THEN
              ALTER TABLE conditions_steps
                ADD CONSTRAINT uk_conditions_steps_condition_step UNIQUE (condition_id, step_id);
            END IF;
          END $$;
          """);
    }
  }
}
