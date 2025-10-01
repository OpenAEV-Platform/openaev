package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_14__Cleanup_cascade_organization extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Adapt user column
    select.execute("ALTER TABLE users DROP CONSTRAINT fk_1483a5e941221f7e;");
    select.execute(
        "ALTER TABLE users "
            + "ADD CONSTRAINT fk_users_organizations "
            + "FOREIGN KEY (user_organization) REFERENCES organizations(organization_id) "
            + "ON DELETE SET NULL;");
    // Cleanup exercises
    select.execute("ALTER TABLE exercises DROP column exercise_animation_group;");
    select.execute("ALTER TABLE exercises DROP column exercise_owner;");
  }
}
