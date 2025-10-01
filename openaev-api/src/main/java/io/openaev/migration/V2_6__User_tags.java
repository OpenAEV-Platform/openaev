package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_6__User_tags extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Add association table between organization and tag
    select.execute(
        """
                CREATE TABLE users_tags (
                    user_id varchar(255) not null constraint user_id_fk references users,
                    tag_id varchar(255) not null constraint tag_id_fk references tags,
                    constraint users_tags_pkey primary key (user_id, tag_id)
                );
                CREATE INDEX idx_users_tags_user on users_tags (user_id);
                CREATE INDEX idx_users_tags_tag on users_tags (tag_id);
                """);
  }
}
