package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_90__add_automatic_finding_hash_column extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      String updateInjectExpectationResult =
          """
          -- necessary for hashing functions
          CREATE SEQUENCE IF NOT EXISTS exercise_launch_order_seq
          AS BIGINT
          INCREMENT BY 1
          START WITH 1;

          ALTER TABLE exercises
          ADD COLUMN exercise_launch_order BIGINT;

          CREATE OR REPLACE FUNCTION update_launch_order_trigger()
          RETURNS TRIGGER AS $$
          BEGIN
              UPDATE exercises
              SET exercise_launch_order = CASE
                  WHEN NEW.exercise_start_date IS NULL
                      THEN NULL
                  ELSE nextval('exercise_launch_order_seq')
                  END
              WHERE exercise_id = NEW.exercise_id;
              RETURN NEW;
          END;
          $$ LANGUAGE plpgsql;

          CREATE OR REPLACE TRIGGER after_insert_exercise
          AFTER INSERT ON exercises
          FOR EACH ROW
          EXECUTE PROCEDURE update_launch_order_trigger();

          CREATE OR REPLACE TRIGGER after_update_exercise_start_date
          AFTER UPDATE OF exercise_start_date ON exercises
          FOR EACH ROW
          EXECUTE PROCEDURE update_launch_order_trigger();

          -- migration of existing records
          UPDATE exercises e
          SET exercise_launch_order = migrated_start_order
          FROM (SELECT exercise_id, nextval('exercise_launch_order_seq') as migrated_start_order
                FROM exercises
                WHERE exercise_start_date IS NOT NULL
                ORDER BY exercise_start_date DESC NULLS LAST) o
          WHERE e.exercise_id = o.exercise_id;
          """;

      statement.executeUpdate(updateInjectExpectationResult);
    }
  }
}
