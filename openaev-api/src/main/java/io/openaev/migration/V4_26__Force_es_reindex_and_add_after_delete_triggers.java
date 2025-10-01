package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_26__Force_es_reindex_and_add_after_delete_triggers extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // FIXME replace those triggers by another method, update the updated at attribute of the
      // table
      statement.executeUpdate(
          """
                  CREATE OR REPLACE FUNCTION update_asset_updated_at_after_delete_finding()
                      RETURNS TRIGGER AS $$
                  BEGIN
                      UPDATE public.assets
                      SET asset_updated_at = now()
                      WHERE asset_id = OLD.asset_id;
                      RETURN OLD;
                  END;
                  $$ LANGUAGE plpgsql;

                  -- Trigger for AFTER DELETE
                  CREATE TRIGGER after_delete_update_asset_updated_at
                      AFTER DELETE ON public.findings_assets
                      FOR EACH ROW EXECUTE FUNCTION update_asset_updated_at_after_delete_finding();
              """);
      statement.executeUpdate(
          """
                    CREATE OR REPLACE FUNCTION update_inject_updated_at_after_delete_team()
                        RETURNS TRIGGER AS $$
                    BEGIN
                        UPDATE public.injects
                        SET inject_updated_at = now()
                        WHERE inject_id = OLD.inject_id;
                        RETURN OLD;
                    END;
                    $$ LANGUAGE plpgsql;

                    -- Trigger for AFTER DELETE
                    CREATE TRIGGER after_delete_update_inject_updated_at
                        AFTER DELETE ON public.injects_teams
                        FOR EACH ROW EXECUTE FUNCTION update_inject_updated_at_after_delete_team();
                """);
      statement.executeUpdate(
          """
                  CREATE OR REPLACE FUNCTION update_exercise_updated_at_after_delete_team()
                      RETURNS TRIGGER AS $$
                  BEGIN
                      UPDATE public.exercises
                      SET exercise_updated_at = now()
                      WHERE exercise_id = OLD.exercise_id;
                      RETURN OLD;
                  END;
                  $$ LANGUAGE plpgsql;

                  -- Trigger for AFTER DELETE
                  CREATE TRIGGER after_delete_update_exercise_updated_at
                      AFTER DELETE ON public.exercises_teams
                      FOR EACH ROW EXECUTE FUNCTION update_exercise_updated_at_after_delete_team();
              """);
      statement.executeUpdate(
          """
                  CREATE OR REPLACE FUNCTION update_scenario_updated_at_after_delete_team()
                      RETURNS TRIGGER AS $$
                  BEGIN
                      UPDATE public.scenarios
                      SET scenario_updated_at = now()
                      WHERE scenario_id = OLD.scenario_id;
                      RETURN OLD;
                  END;
                  $$ LANGUAGE plpgsql;

                  -- Trigger for AFTER DELETE
                  CREATE TRIGGER after_delete_update_scenario_updated_at
                      AFTER DELETE ON public.scenarios_teams
                      FOR EACH ROW EXECUTE FUNCTION update_scenario_updated_at_after_delete_team();
              """);
      // re-index all in ES for the deletions
      statement.executeUpdate("DELETE FROM indexing_status;");
    }
  }
}
