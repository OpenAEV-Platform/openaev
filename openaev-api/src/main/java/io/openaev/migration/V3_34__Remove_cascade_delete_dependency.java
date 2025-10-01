package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_34__Remove_cascade_delete_dependency extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute("ALTER TABLE injects DROP CONSTRAINT fk_depends_from_another;");
    select.execute(
        "ALTER TABLE injects ADD CONSTRAINT fk_depends_from_another "
            + "FOREIGN KEY (inject_depends_from_another) REFERENCES injects ON DELETE SET NULL;");
  }
}
