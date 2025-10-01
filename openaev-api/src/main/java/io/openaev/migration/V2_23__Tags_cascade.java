package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_23__Tags_cascade extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Injects
    select.execute("ALTER TABLE injects_tags DROP CONSTRAINT tag_id_fk;");
    select.execute("ALTER TABLE injects_tags DROP CONSTRAINT inject_id_fk;");
    select.execute(
        "ALTER TABLE injects_tags "
            + "ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES tags(tag_id)  "
            + "ON DELETE CASCADE;");
    select.execute(
        "ALTER TABLE injects_tags "
            + "ADD CONSTRAINT inject_id_fk FOREIGN KEY (inject_id) REFERENCES injects(inject_id)  "
            + "ON DELETE CASCADE;");
    // Exercises
    select.execute("ALTER TABLE exercises_tags DROP CONSTRAINT tag_id_fk;");
    select.execute("ALTER TABLE exercises_tags DROP CONSTRAINT exercise_id_fk;");
    select.execute(
        "ALTER TABLE exercises_tags "
            + "ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES tags(tag_id)  "
            + "ON DELETE CASCADE;");
    select.execute(
        "ALTER TABLE exercises_tags "
            + "ADD CONSTRAINT exercise_id_fk FOREIGN KEY (exercise_id) REFERENCES exercises(exercise_id)  "
            + "ON DELETE CASCADE;");
    // Organizations
    select.execute("ALTER TABLE organizations_tags DROP CONSTRAINT tag_id_fk;");
    select.execute("ALTER TABLE organizations_tags DROP CONSTRAINT organization_id_fk;");
    select.execute(
        "ALTER TABLE organizations_tags "
            + "ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES tags(tag_id)  "
            + "ON DELETE CASCADE;");
    select.execute(
        "ALTER TABLE organizations_tags "
            + "ADD CONSTRAINT organization_id_fk FOREIGN KEY (organization_id) REFERENCES organizations(organization_id)  "
            + "ON DELETE CASCADE;");
    // Users
    select.execute("ALTER TABLE users_tags DROP CONSTRAINT tag_id_fk;");
    select.execute("ALTER TABLE users_tags DROP CONSTRAINT user_id_fk;");
    select.execute(
        "ALTER TABLE users_tags "
            + "ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES tags(tag_id)  "
            + "ON DELETE CASCADE;");
    select.execute(
        "ALTER TABLE users_tags "
            + "ADD CONSTRAINT user_id_fk FOREIGN KEY (user_id) REFERENCES users(user_id)  "
            + "ON DELETE CASCADE;");
  }
}
