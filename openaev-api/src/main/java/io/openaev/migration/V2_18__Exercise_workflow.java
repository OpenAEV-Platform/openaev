package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_18__Exercise_workflow extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Exercise
    select.execute(
        "ALTER TABLE exercises ADD exercise_status varchar(255) not null default 'SCHEDULED';");
    select.execute("ALTER TABLE exercises ADD exercise_pause_date timestamp;");
    // Add pauses
    select.execute(
        """
                            create table pauses
                            (
                                pause_id varchar(255) not null constraint pauses_pkey primary key,
                                pause_exercise varchar(255) not null
                                    constraint fk_pause_exercise references exercises on delete cascade,
                                pause_date timestamp(0) with time zone not null,
                                pause_duration bigint
                            );
                            create index idx_pauses on pauses (pause_id);
                """);
  }
}
