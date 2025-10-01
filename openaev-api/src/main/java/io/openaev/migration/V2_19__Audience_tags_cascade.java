package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_19__Audience_tags_cascade extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Fix audiences constraints
    select.execute("ALTER TABLE audiences_tags DROP CONSTRAINT audience_id_fk;");
    select.execute("ALTER TABLE audiences_tags DROP CONSTRAINT tag_id_fk;");
    select.execute(
        "ALTER TABLE audiences_tags "
            + "ADD CONSTRAINT audience_id_fk "
            + "FOREIGN KEY (audience_id) REFERENCES audiences(audience_id) "
            + "ON DELETE CASCADE ;");
    select.execute(
        "ALTER TABLE audiences_tags "
            + "ADD CONSTRAINT tag_id_fk "
            + "FOREIGN KEY (tag_id) REFERENCES tags(tag_id) "
            + "ON DELETE CASCADE ;");
  }
}
