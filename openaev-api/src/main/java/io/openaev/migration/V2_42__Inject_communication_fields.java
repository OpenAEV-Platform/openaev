package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_42__Inject_communication_fields extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute("ALTER TABLE communications ADD communication_content_html text;");
    select.execute("ALTER TABLE communications ADD communication_from text NOT NULL;");
  }
}
