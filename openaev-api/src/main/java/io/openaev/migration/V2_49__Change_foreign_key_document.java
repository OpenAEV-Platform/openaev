package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_49__Change_foreign_key_document extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Adapt user column
    select.execute("ALTER TABLE exercises DROP CONSTRAINT fk_exercise_logo_dark;");
    select.execute("ALTER TABLE exercises DROP CONSTRAINT fk_exercise_logo_light;");

    select.execute("ALTER TABLE medias DROP CONSTRAINT fk_media_logo_dark;");
    select.execute("ALTER TABLE medias DROP CONSTRAINT fk_media_logo_light;");

    select.execute(
        "ALTER TABLE exercises ADD constraint fk_exercise_logo_dark foreign key (exercise_logo_dark) references documents on delete set null;");
    select.execute(
        "ALTER TABLE exercises ADD constraint fk_exercise_logo_light foreign key (exercise_logo_light) references documents on delete set null;");

    select.execute(
        "ALTER TABLE medias ADD constraint fk_media_logo_dark foreign key (media_logo_dark) references documents on delete set null;");
    select.execute(
        "ALTER TABLE medias ADD constraint fk_media_logo_light foreign key (media_logo_light) references documents on delete set null;");
  }
}
