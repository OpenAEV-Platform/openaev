package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_33__Add_Index_inject extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute(
        ""
            + "CREATE INDEX idx_inject_inject_injector_contract on injects (inject_injector_contract);"
            + "CREATE INDEX idx_injector_contract_injector on injectors_contracts (injector_id);"
            + "");
  }
}
