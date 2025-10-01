package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_24__Improve_dryrun extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute("ALTER TABLE dryruns DROP column dryrun_status;");
    // Add association table between organization and tag
    select.execute(
        """
                CREATE TABLE dryruns_users (
                    dryrun_id varchar(255) not null constraint dryrun_id_fk
                        references dryruns on delete cascade,
                    user_id varchar(255) not null constraint user_id_fk
                        references users on delete cascade,
                    constraint dryruns_users_pkey primary key (user_id, dryrun_id)
                );
                CREATE INDEX idx_dryruns_users_user on dryruns_users (user_id);
                CREATE INDEX idx_dryruns_users_dryrun on dryruns_users (dryrun_id);
                """);
  }
}
