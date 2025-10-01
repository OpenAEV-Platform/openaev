package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_33__Inject_not_null extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Injects
    select.execute("ALTER TABLE injects ALTER COLUMN inject_contract SET NOT NULL");
    select.execute("ALTER TABLE injects ALTER COLUMN inject_type SET NOT NULL");
  }
}
