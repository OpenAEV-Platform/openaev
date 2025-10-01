package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_91__Custom_inject_contracts extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute(
        "ALTER TABLE injectors_contracts ADD injector_contract_custom bool default false;");
    select.execute("ALTER TABLE injectors DROP injector_contract_template;");
  }
}
