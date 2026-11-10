package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Deletes the grants of a resource when the resource is deleted (#8921).
 *
 * <p>A grant names its resource by {@code grant_resource} and {@code grant_resource_type}, with no
 * foreign key since V4_24 made the table generic, so nothing removed the grants of a deleted
 * scenario, simulation, atomic testing, autonomous run or injector contract: they accumulated and
 * applied again if the id was registered anew. Every delete path (JPA, native queries, the tenant
 * cascade) reaches the row delete, so the rule lives in the database, as the notification rules of
 * a scenario do since V3_82.
 *
 * <p>Injector contract ids repeat across tenants (the key is the contract id and the tenant): a
 * contract deleted in one tenant takes the grants of that tenant's groups, and the grants of
 * platform groups only once no tenant has the contract any more, since a platform group's grant
 * applies in every tenant.
 *
 * <p>The existing orphans are removed in the same migration.
 */
@Component
public class V6_20261106100000000__Delete_grants_with_their_resource extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION delete_grants_of_deleted_scenario() RETURNS TRIGGER AS $$
          BEGIN
            DELETE FROM grants
            WHERE grant_resource_type = 'SCENARIO' AND grant_resource = OLD.scenario_id;
            RETURN OLD;
          END;
          $$ LANGUAGE plpgsql;

          CREATE OR REPLACE FUNCTION delete_grants_of_deleted_exercise() RETURNS TRIGGER AS $$
          BEGIN
            DELETE FROM grants
            WHERE grant_resource_type = 'SIMULATION' AND grant_resource = OLD.exercise_id;
            RETURN OLD;
          END;
          $$ LANGUAGE plpgsql;

          CREATE OR REPLACE FUNCTION delete_grants_of_deleted_inject() RETURNS TRIGGER AS $$
          BEGIN
            DELETE FROM grants
            WHERE grant_resource_type = 'ATOMIC_TESTING' AND grant_resource = OLD.inject_id;
            RETURN OLD;
          END;
          $$ LANGUAGE plpgsql;

          CREATE OR REPLACE FUNCTION delete_grants_of_deleted_autonomous_run() RETURNS TRIGGER AS $$
          BEGIN
            DELETE FROM grants
            WHERE grant_resource_type = 'AUTONOMOUS_RUN' AND grant_resource = OLD.autonomous_run_id;
            RETURN OLD;
          END;
          $$ LANGUAGE plpgsql;

          CREATE OR REPLACE FUNCTION delete_grants_of_deleted_injector_contract() RETURNS TRIGGER AS $$
          BEGIN
            DELETE FROM grants gr
            USING groups g
            WHERE gr.grant_group = g.group_id
              AND gr.grant_resource_type = 'THREAT_ARSENAL'
              AND gr.grant_resource = OLD.injector_contract_id
              AND (
                g.tenant_id = OLD.tenant_id
                OR (
                  g.tenant_id IS NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM injectors_contracts ic
                    WHERE ic.injector_contract_id = OLD.injector_contract_id
                  )
                )
              );
            RETURN OLD;
          END;
          $$ LANGUAGE plpgsql;

          DROP TRIGGER IF EXISTS trg_delete_scenario_grants ON scenarios;
          CREATE TRIGGER trg_delete_scenario_grants
            AFTER DELETE ON scenarios
            FOR EACH ROW EXECUTE FUNCTION delete_grants_of_deleted_scenario();

          DROP TRIGGER IF EXISTS trg_delete_exercise_grants ON exercises;
          CREATE TRIGGER trg_delete_exercise_grants
            AFTER DELETE ON exercises
            FOR EACH ROW EXECUTE FUNCTION delete_grants_of_deleted_exercise();

          DROP TRIGGER IF EXISTS trg_delete_inject_grants ON injects;
          CREATE TRIGGER trg_delete_inject_grants
            AFTER DELETE ON injects
            FOR EACH ROW EXECUTE FUNCTION delete_grants_of_deleted_inject();

          DROP TRIGGER IF EXISTS trg_delete_autonomous_run_grants ON autonomous_runs;
          CREATE TRIGGER trg_delete_autonomous_run_grants
            AFTER DELETE ON autonomous_runs
            FOR EACH ROW EXECUTE FUNCTION delete_grants_of_deleted_autonomous_run();

          DROP TRIGGER IF EXISTS trg_delete_injector_contract_grants ON injectors_contracts;
          CREATE TRIGGER trg_delete_injector_contract_grants
            AFTER DELETE ON injectors_contracts
            FOR EACH ROW EXECUTE FUNCTION delete_grants_of_deleted_injector_contract();

          DELETE FROM grants gr
          WHERE (gr.grant_resource_type = 'SCENARIO'
                 AND NOT EXISTS (SELECT 1 FROM scenarios s WHERE s.scenario_id = gr.grant_resource))
             OR (gr.grant_resource_type = 'SIMULATION'
                 AND NOT EXISTS (SELECT 1 FROM exercises e WHERE e.exercise_id = gr.grant_resource))
             OR (gr.grant_resource_type = 'ATOMIC_TESTING'
                 AND NOT EXISTS (SELECT 1 FROM injects i WHERE i.inject_id = gr.grant_resource))
             OR (gr.grant_resource_type = 'AUTONOMOUS_RUN'
                 AND NOT EXISTS (
                   SELECT 1 FROM autonomous_runs ar WHERE ar.autonomous_run_id = gr.grant_resource))
             OR (gr.grant_resource_type = 'THREAT_ARSENAL'
                 AND NOT EXISTS (
                   SELECT 1 FROM injectors_contracts ic
                   JOIN groups g ON g.group_id = gr.grant_group
                   WHERE ic.injector_contract_id = gr.grant_resource
                     AND (g.tenant_id IS NULL OR g.tenant_id = ic.tenant_id)));
          """);
    }
  }
}
