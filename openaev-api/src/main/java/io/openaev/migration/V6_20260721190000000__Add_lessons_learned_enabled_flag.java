package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * The lessons learned module becomes opt-in: a new enabled flag on exercises and scenarios,
 * disabled by default. Existing entities that already use the module (lessons categories,
 * objectives, or an anonymized questionnaire) are backfilled to enabled so production instances
 * keep their lessons learned tab.
 */
@Component
public class V6_20260721190000000__Add_lessons_learned_enabled_flag extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              ALTER TABLE exercises
              ADD COLUMN IF NOT EXISTS exercise_lessons_enabled BOOLEAN NOT NULL DEFAULT FALSE
              """);
      statement.execute(
          """
              ALTER TABLE scenarios
              ADD COLUMN IF NOT EXISTS scenario_lessons_enabled BOOLEAN NOT NULL DEFAULT FALSE
              """);
      // Backfill: keep the module enabled where it is already in use.
      statement.execute(
          """
              UPDATE exercises e SET exercise_lessons_enabled = TRUE
              WHERE e.exercise_lessons_anonymized = TRUE
                 OR EXISTS (SELECT 1 FROM lessons_categories lc WHERE lc.lessons_category_exercise = e.exercise_id)
                 OR EXISTS (SELECT 1 FROM objectives o WHERE o.objective_exercise = e.exercise_id)
              """);
      statement.execute(
          """
              UPDATE scenarios s SET scenario_lessons_enabled = TRUE
              WHERE s.scenario_lessons_anonymized = TRUE
                 OR EXISTS (SELECT 1 FROM lessons_categories lc WHERE lc.lessons_category_scenario = s.scenario_id)
                 OR EXISTS (SELECT 1 FROM objectives o WHERE o.objective_scenario = s.scenario_id)
              """);
    }
  }
}

// -- ROLLBACK --
// ALTER TABLE exercises DROP COLUMN IF EXISTS exercise_lessons_enabled;
// ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_lessons_enabled;
