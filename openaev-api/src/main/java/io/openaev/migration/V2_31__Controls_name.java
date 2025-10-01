package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_31__Controls_name extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Comchecks
    select.execute("ALTER TABLE comchecks ADD comcheck_name varchar(256);");
    // Comchecks
    select.execute("ALTER TABLE dryruns ADD dryrun_name varchar(256);");
  }
}
