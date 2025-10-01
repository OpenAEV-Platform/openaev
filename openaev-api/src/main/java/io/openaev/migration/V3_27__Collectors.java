package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_27__Collectors extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute("ALTER TABLE collectors ADD column collector_security_platform varchar(255);");
    select.execute(
        "ALTER TABLE collectors ADD constraint fk_collector_security_platform foreign key (collector_security_platform) references assets on delete set null;");
  }
}
