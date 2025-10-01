package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_9__Improve_model extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // User
    select.execute("ALTER TABLE users DROP column user_latitude;");
    select.execute("ALTER TABLE users DROP column user_longitude;");
    select.execute("ALTER TABLE users ADD user_country varchar(255);");
    select.execute("ALTER TABLE users ADD user_city varchar(255);");
    // Inject
    select.execute("ALTER TABLE injects DROP column inject_latitude;");
    select.execute("ALTER TABLE injects DROP column inject_longitude;");
    select.execute("ALTER TABLE injects ADD inject_country varchar(255);");
    select.execute("ALTER TABLE injects ADD inject_city varchar(255);");
  }
}
