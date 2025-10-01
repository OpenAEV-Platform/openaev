package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_45__Media_upgrade extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Remove media and exercises fields
    select.execute("ALTER TABLE medias DROP column media_color;");
    select.execute("ALTER TABLE medias DROP column media_published;");
    select.execute("ALTER TABLE exercises DROP column exercise_image;");
    // Add new required fields
    select.execute("ALTER TABLE medias ADD media_type varchar(255);");
    select.execute("ALTER TABLE medias ADD media_description text;");
    select.execute("ALTER TABLE medias ADD media_logo_dark varchar(255);");
    select.execute("ALTER TABLE medias ADD media_logo_light varchar(255);");
    select.execute("ALTER TABLE medias ADD media_primary_color_dark varchar(255);");
    select.execute("ALTER TABLE medias ADD media_primary_color_light varchar(255);");
    select.execute("ALTER TABLE medias ADD media_secondary_color_dark varchar(255);");
    select.execute("ALTER TABLE medias ADD media_secondary_color_light varchar(255);");
    select.execute("ALTER TABLE exercises ADD exercise_logo_dark varchar(255);");
    select.execute("ALTER TABLE exercises ADD exercise_logo_light varchar(255);");
    select.execute(
        "ALTER TABLE exercises ADD constraint fk_exercise_logo_dark foreign key (exercise_logo_dark) references documents on delete cascade;");
    select.execute(
        "ALTER TABLE exercises ADD constraint fk_exercise_logo_light foreign key (exercise_logo_light) references documents on delete cascade;");
    select.execute(
        "ALTER TABLE medias ADD constraint fk_media_logo_dark foreign key (media_logo_dark) references documents on delete cascade;");
    select.execute(
        "ALTER TABLE medias ADD constraint fk_media_logo_light foreign key (media_logo_light) references documents on delete cascade;");
  }
}
