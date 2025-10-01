package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_67__Update_Execution_traces_status_asset_inactive extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute(
        """
                UPDATE execution_traces SET execution_status = 'AGENT_INACTIVE' WHERE execution_status='ASSET_INACTIVE';
""");
    select.execute(
        """
                UPDATE execution_traces SET execution_message = REPLACE(execution_message, 'Asset error', 'Agent error') WHERE execution_message LIKE '%Asset error%';
""");
  }
}
