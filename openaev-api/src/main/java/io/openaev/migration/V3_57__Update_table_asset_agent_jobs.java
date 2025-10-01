package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_57__Update_table_asset_agent_jobs extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Replace asset_agent_asset (asset_id foreign key) column by asset_agent_agent (agent_id
    // foreign key)
    select.execute(
        """
                ALTER TABLE asset_agent_jobs ADD COLUMN asset_agent_agent VARCHAR(255);
                ALTER TABLE asset_agent_jobs ADD CONSTRAINT asset_agent_agent_fk FOREIGN KEY (asset_agent_agent) REFERENCES agents(agent_id) ON DELETE CASCADE;
                UPDATE asset_agent_jobs aaj SET asset_agent_agent=a.agent_id FROM (SELECT agent_id, agent_asset FROM agents) AS a WHERE a.agent_asset=aaj.asset_agent_asset;
                ALTER TABLE asset_agent_jobs DROP COLUMN asset_agent_asset;
            """);
  }
}
