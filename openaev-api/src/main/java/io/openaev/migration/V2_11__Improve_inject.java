package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_11__Improve_inject extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Add association table between inject and tag
    select.execute(
        """
                CREATE TABLE injects_tags (
                    inject_id varchar(255) not null constraint inject_id_fk references injects,
                    tag_id varchar(255) not null constraint tag_id_fk references tags,
                    constraint injects_tags_pkey primary key (inject_id, tag_id)
                );
                CREATE INDEX idx_injects_tags_inject on injects_tags (inject_id);
                CREATE INDEX idx_injects_tags_tag on injects_tags (tag_id);
                """);
  }
}
