package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_15__Add_column_injector_contract_external_id extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement stmt = connection.createStatement();
    stmt.execute(
        """
          ALTER TABLE injectors_contracts
            ADD COLUMN injector_contract_external_id VARCHAR UNIQUE;
          """);
  }

  /* ROLLBACK
   ALTER TABLE injectors_contracts DROP COLUMN injector_contract_external_id;
  */
}
