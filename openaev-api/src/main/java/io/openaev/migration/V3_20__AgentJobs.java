package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_20__AgentJobs extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute("ALTER TABLE assets ADD column endpoint_agent_version varchar(255);");
    // Create table
    select.execute(
        """
          CREATE TABLE asset_agent_jobs (
            asset_agent_id varchar(255) not null constraint asset_agent_pkey primary key,
            asset_agent_created_at timestamp not null default now(),
            asset_agent_updated_at timestamp not null default now(),
            asset_agent_inject varchar(255)
              constraint asset_agent_inject_fk
                  references injects
                  on delete cascade,
            asset_agent_asset varchar(255)
              constraint asset_agent_asset_fk
                  references assets
                  on delete cascade,
            asset_agent_command text
          );
          CREATE INDEX idx_asset_agent_jobs on asset_agent_jobs (asset_agent_id);
     """);
  }
}
