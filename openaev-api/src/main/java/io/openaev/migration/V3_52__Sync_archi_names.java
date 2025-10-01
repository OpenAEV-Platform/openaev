package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_52__Sync_archi_names extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();
    statement.execute(
        "UPDATE payloads SET payload_execution_arch = 'x86_64' WHERE payload_execution_arch = 'X86_64';");
    statement.execute(
        "UPDATE payloads SET payload_execution_arch = 'arm64' WHERE payload_execution_arch = 'ARM64';");
  }
}
