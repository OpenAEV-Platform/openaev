package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_44__Add_column_executable_arch extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();
    statement.execute("ALTER TABLE payloads ADD executable_arch varchar(255);");
    statement.execute(
        "UPDATE payloads SET executable_arch = 'x86_64' WHERE payload_type ='Executable';");
  }
}
