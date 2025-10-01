package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_17__Payloads extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        "ALTER TABLE payloads RENAME COLUMN network_traffic_ip TO network_traffic_ip_src;");
    select.execute("ALTER TABLE payloads ADD column network_traffic_ip_dst text;");
    select.execute("ALTER TABLE payloads ADD column network_traffic_port_src int;");
    select.execute("ALTER TABLE payloads ADD column network_traffic_port_dst int;");
    select.execute("ALTER TABLE payloads ADD column network_traffic_protocol varchar(255);");
  }
}
