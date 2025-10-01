package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_4__Creation_update_cleanup extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Cleanup
    select.execute("ALTER TABLE users DROP column user_planificateur;");
    select.execute("ALTER TABLE users DROP column user_phone3;");
    select.execute("ALTER TABLE users DROP column user_email2;");
    // Add color to tag
    select.execute("ALTER TABLE tags ADD tag_color varchar(255) default '#01478DFF';");
    // Add association table between exercise and tag
    select.execute(
        """
                CREATE TABLE exercises_tags (
                    exercise_id varchar(255) not null constraint exercise_id_fk references exercises,
                    tag_id varchar(255) not null constraint tag_id_fk references tags,
                    constraint exercises_tags_pkey primary key (exercise_id, tag_id)
                );
                CREATE INDEX idx_exercises_tags_exercise on exercises_tags (exercise_id);
                CREATE INDEX idx_exercises_tags_exercise_tag on exercises_tags (tag_id);
                """);
    // created_at / updated_at
    // exercise
    select.execute(
        "ALTER TABLE exercises ADD exercise_created_at timestamp not null default now();");
    select.execute(
        "ALTER TABLE exercises ADD exercise_updated_at timestamp not null default now();");
    // user
    select.execute("ALTER TABLE users ADD user_created_at timestamp not null default now();");
    select.execute("ALTER TABLE users ADD user_updated_at timestamp not null default now();");
    // inject
    select.execute("ALTER TABLE injects ADD inject_created_at timestamp not null default now();");
    select.execute("ALTER TABLE injects ADD inject_updated_at timestamp not null default now();");
  }
}
