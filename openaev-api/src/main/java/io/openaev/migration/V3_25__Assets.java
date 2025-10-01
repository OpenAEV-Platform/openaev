package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_25__Assets extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute("ALTER TABLE assets ADD column security_platform_type varchar(255);");
    select.execute("ALTER TABLE assets ADD column security_platform_logo_light varchar(255);");
    select.execute("ALTER TABLE assets ADD column security_platform_logo_dark varchar(255);");
    select.execute(
        "ALTER TABLE assets ADD constraint fk_security_platform_logo_light foreign key (security_platform_logo_light) references documents on delete set null;");
    select.execute(
        "ALTER TABLE assets ADD constraint fk_security_platform_logo_dark foreign key (security_platform_logo_dark) references documents on delete set null;");
  }
}
