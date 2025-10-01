package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_15__Injector_contracts_Payloads extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Purge
    select.execute(
        "ALTER TABLE injectors_contracts ADD column injector_contract_payload varchar(255) constraint injector_contract_payload_fk references payloads on delete cascade;");
    select.execute(
        "CREATE UNIQUE INDEX injector_contract_payload_unique on injectors_contracts (injector_contract_payload, injector_id);");
  }
}
