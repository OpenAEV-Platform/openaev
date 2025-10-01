package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_79__Update_CS_agents_id extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Update database to get CS agents external reference = agent_id
    select.execute(
        """
                    -- Clone CS lines with agent_id = agent_external_reference
                    INSERT INTO agents (agent_id, agent_asset, agent_privilege, agent_deployment_mode, agent_executed_by_user,
                                        agent_executor, agent_version, agent_parent, agent_inject, agent_process_name,
                                        agent_external_reference, agent_last_seen, agent_created_at, agent_updated_at, agent_cleared_at)
                    SELECT agent_external_reference, agent_asset, agent_privilege, agent_deployment_mode, agent_executed_by_user,
                           agent_executor, agent_version, agent_parent, agent_inject, agent_process_name,
                           agent_external_reference, agent_last_seen, agent_created_at, agent_updated_at, agent_cleared_at FROM agents a LEFT JOIN executors ex ON a.agent_executor = ex.executor_id
                               WHERE a.agent_parent is null and a.agent_inject is null and ex.executor_type = 'openaev_crowdstrike';
                    -- Create temp agents ids table to update all agent_id foreign keys with external reference
                    CREATE TABLE agents_ids_temp as (SELECT agent_id, agent_external_reference FROM agents a LEFT JOIN executors ex ON a.agent_executor = ex.executor_id
                                 WHERE a.agent_parent is null and a.agent_inject is null and ex.executor_type = 'openaev_crowdstrike' and a.agent_id <> a.agent_external_reference);
                    -- Update all agent_id foreign keys with external reference
                    UPDATE injects_expectations ie set agent_id = ag.agent_external_reference from
                        (SELECT agent_id, agent_external_reference FROM agents_ids_temp) as ag
                        WHERE ie.agent_id = ag.agent_id;
                    UPDATE execution_traces et set execution_agent_id = ag.agent_external_reference from
                        (SELECT agent_id, agent_external_reference FROM agents_ids_temp) as ag
                        WHERE et.execution_agent_id = ag.agent_id;
                    UPDATE asset_agent_jobs aaj set asset_agent_agent = ag.agent_external_reference from
                        (SELECT agent_id, agent_external_reference FROM agents_ids_temp) as ag
                        WHERE aaj.asset_agent_agent = ag.agent_id;
                    UPDATE agents a set agent_parent = ag.agent_external_reference from
                        (SELECT agent_id, agent_external_reference FROM agents_ids_temp) as ag
                        WHERE a.agent_parent = ag.agent_id;
                    -- Delete older CS agents
                    DELETE FROM agents WHERE agent_id in (select agent_id from agents_ids_temp);
                    -- Drop temp table
                    DROP TABLE agents_ids_temp;
            """);
  }
}
