package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_51__Add_architecture_to_payload extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();
    statement.execute(
        "UPDATE payloads SET executable_arch = 'X86_64'  WHERE executable_arch = 'x86_64';");
    statement.execute(
        "UPDATE payloads SET executable_arch = 'ARM64'  WHERE executable_arch = 'arm64';");
    statement.execute(
        "UPDATE payloads SET executable_arch = 'ALL_ARCHITECTURES' WHERE executable_arch IS NULL;");
    statement.execute(
        "ALTER TABLE payloads ALTER COLUMN executable_arch SET DEFAULT 'ALL_ARCHITECTURES'");
    statement.execute("ALTER TABLE payloads ALTER COLUMN executable_arch SET NOT NULL");
    statement.execute(
        "ALTER TABLE payloads RENAME COLUMN executable_arch TO payload_execution_arch");
  }
}
