package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_60__Alter_xls_mapper_table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();

    statement.execute(
        "ALTER TABLE inject_importers alter column importer_mapper_id drop not null;");
    statement.execute(
        "ALTER TABLE inject_importers alter column importer_injector_contract_id drop not null;");
    statement.execute(
        "ALTER TABLE rule_attributes alter column attribute_inject_importer_id drop not null;");
  }
}
