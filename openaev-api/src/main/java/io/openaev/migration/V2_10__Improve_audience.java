package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_10__Improve_audience extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute("ALTER TABLE audiences ADD audience_description text;");
    // Add association table between audience and tag
    select.execute(
        """
                CREATE TABLE audiences_tags (
                    audience_id varchar(255) not null constraint audience_id_fk references audiences,
                    tag_id varchar(255) not null constraint tag_id_fk references tags,
                    constraint audiences_tags_pkey primary key (audience_id, tag_id)
                );
                CREATE INDEX idx_audiences_tags_audience on audiences_tags (audience_id);
                CREATE INDEX idx_audiences_tags_tag on audiences_tags (tag_id);
                """);
  }
}
