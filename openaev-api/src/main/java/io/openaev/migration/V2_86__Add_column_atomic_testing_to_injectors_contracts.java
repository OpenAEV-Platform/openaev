package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_86__Add_column_atomic_testing_to_injectors_contracts extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        """
        ALTER TABLE injectors_contracts ADD COLUMN injector_contract_atomic_testing bool not null default true;
        """);
    // Update injects contracts
    select.executeUpdate(
        "UPDATE injectors_contracts SET injector_contract_atomic_testing='false' WHERE injectors_contracts.injector_contract_id = 'd02e9132-b9d0-4daa-b3b1-4b9871f8472c';");
    select.executeUpdate(
        "UPDATE injectors_contracts SET injector_contract_atomic_testing='false' WHERE injectors_contracts.injector_contract_id = 'fb5e49a2-6366-4492-b69a-f9b9f39a533e';");
    select.executeUpdate(
        "UPDATE injectors_contracts SET injector_contract_atomic_testing='false' WHERE injectors_contracts.injector_contract_id = 'f8e70b27-a69c-4b9f-a2df-e217c36b3981';");
  }
}
