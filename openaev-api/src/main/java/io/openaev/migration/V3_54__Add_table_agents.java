package io.openaev.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_54__Add_table_agents extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute(
        """
                        CREATE TABLE agents (
                            agent_id VARCHAR(255) NOT NULL CONSTRAINT agents_pkey PRIMARY KEY,
                            agent_asset VARCHAR(255) NOT NULL CONSTRAINT agent_asset_id_fk REFERENCES assets ON DELETE CASCADE,
                            agent_privilege VARCHAR(255) NOT NULL,
                            agent_deployment_mode VARCHAR(255) NOT NULL,
                            agent_executed_by_user VARCHAR(255) NOT NULL,
                            agent_executor VARCHAR(255) CONSTRAINT agent_executor_id_fk REFERENCES executors ON DELETE CASCADE,
                            agent_version VARCHAR(255),
                            agent_parent VARCHAR(255),
                            agent_inject VARCHAR(255) CONSTRAINT agent_inject_id_fk REFERENCES injects ON DELETE CASCADE,
                            agent_process_name VARCHAR(255),
                            agent_external_reference VARCHAR(255),
                            agent_last_seen TIMESTAMP,
                            agent_created_at TIMESTAMP DEFAULT now(),
                            agent_updated_at TIMESTAMP DEFAULT now()
                          );
                          CREATE INDEX idx_agent_assets ON agents(agent_asset);
                          ALTER TABLE agents ADD CONSTRAINT agent_parent_id_fk FOREIGN KEY (agent_parent) REFERENCES agents(agent_id) ON DELETE CASCADE;
                """);

    // Migration datas
    ResultSet resultAgentExecutor =
        select.executeQuery(
            "SELECT executor_id FROM executors where executor_type='openaev_caldera'");
    String executorCalderaId = "";
    if (resultAgentExecutor.next()) {
      executorCalderaId = resultAgentExecutor.getString("executor_id");
    }
    ResultSet results =
        select.executeQuery(
            "SELECT * FROM assets WHERE asset_type='Endpoint' AND asset_executor IS NOT NULL");
    PreparedStatement statement =
        context
            .getConnection()
            .prepareStatement(
                """
                INSERT INTO agents(agent_id, agent_asset, agent_privilege, agent_deployment_mode, agent_executed_by_user, agent_executor,
                                   agent_version, agent_parent, agent_inject, agent_process_name, agent_external_reference, agent_last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
    while (results.next()) {
      statement.setObject(1, UUID.randomUUID().toString());
      statement.setString(2, results.getString("asset_id"));
      statement.setString(3, "admin");
      statement.setString(
          4, executorCalderaId.equals(results.getString("asset_executor")) ? "session" : "service");
      statement.setString(
          5,
          "Windows".equals(results.getString("endpoint_platform"))
              ? "nt authority\\system"
              : "root");
      statement.setString(6, results.getString("asset_executor"));
      statement.setString(7, results.getString("endpoint_agent_version"));
      statement.setString(8, results.getString("asset_parent"));
      statement.setString(9, results.getString("asset_inject"));
      statement.setString(10, results.getString("asset_process_name"));
      statement.setString(11, results.getString("asset_external_reference"));
      statement.setTimestamp(12, results.getTimestamp("asset_last_seen"));
      statement.addBatch();
    }
    statement.executeBatch();

    // Remove old columns
    Statement removeColumns = context.getConnection().createStatement();
    removeColumns.execute(
        """
        ALTER TABLE assets DROP COLUMN endpoint_agent_version;
        ALTER TABLE assets DROP COLUMN asset_last_seen;
        ALTER TABLE assets DROP COLUMN asset_executor;
        -- can't drop asset_external_reference because used for securityPlatform
        ALTER TABLE assets DROP COLUMN asset_parent;
        ALTER TABLE assets DROP COLUMN asset_inject;
        ALTER TABLE assets DROP COLUMN asset_process_name;
""");
  }
}
