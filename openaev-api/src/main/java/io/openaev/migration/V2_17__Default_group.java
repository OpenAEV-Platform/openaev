package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_17__Default_group extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Exercise
    select.execute("ALTER TABLE groups ADD group_default_user_assign bool default false;");
    select.execute(
        """
                create table groups_exercises_default_grants
                (
                    group_id varchar(255) not null
                        constraint fk_group_id
                            references groups,
                    exercises_default_grants varchar(255)
                );
                """);
  }
}
