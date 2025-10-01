package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_12__Payloads extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Purge
    select.execute("TRUNCATE payloads CASCADE;");
    select.execute("ALTER TABLE payloads DROP column payload_content;");
    select.execute("ALTER TABLE payloads ADD column payload_platforms text[];");
    select.execute("ALTER TABLE payloads ADD column command_executor varchar(255);");
    select.execute("ALTER TABLE payloads ADD column command_content text;");
    select.execute(
        "ALTER TABLE payloads ADD column executable_file varchar(255) constraint executable_file_fk references documents on delete cascade;");
    select.execute(
        "ALTER TABLE payloads ADD column file_drop_file varchar(255) constraint file_drop_file_fk references documents on delete cascade;");
    select.execute("ALTER TABLE payloads ADD column dns_resolution_hostname text;");
    select.execute("ALTER TABLE payloads ADD column network_traffic_ip text;");
    select.execute("ALTER TABLE payloads ADD column payload_cleanup_executor varchar(255);");
    select.execute("ALTER TABLE payloads ADD column payload_cleanup_command text;");
    select.execute("ALTER TABLE payloads ADD column payload_arguments json;");
    select.execute("ALTER TABLE payloads ADD column payload_prerequisites json;");
  }
}
