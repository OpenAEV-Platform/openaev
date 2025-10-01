package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_28__Dryinject_link extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Cleanups
    select.execute("DELETE FROM dryinjects_statuses;");
    select.execute("DELETE FROM dryinjects;");
    // Remove duplicated content
    select.execute("ALTER TABLE dryinjects DROP column dryinject_title;");
    select.execute("ALTER TABLE dryinjects DROP column dryinject_content;");
    select.execute("ALTER TABLE dryinjects DROP column dryinject_type;");
    // Link dryinject to original inject
    select.execute("ALTER TABLE dryinjects ADD dryinject_inject varchar(256);");
    select.execute(
        "ALTER TABLE dryinjects "
            + "ADD CONSTRAINT fk_dryinjects_injects "
            + "FOREIGN KEY (dryinject_inject) REFERENCES injects(inject_id) "
            + "ON DELETE SET NULL;");
  }
}
