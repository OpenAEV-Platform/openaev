package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_1__Relative_inject extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Create the structure
    select.execute("ALTER TABLE injects add inject_depends_duration bigint;");
    select.execute("ALTER TABLE injects add inject_depends_from_another varchar(255);");
    select.execute(
        "ALTER TABLE injects add constraint fk_depends_from_another "
            + "foreign key (inject_depends_from_another) references injects on delete cascade;");
    // Migration the data
    select.executeUpdate(
        """
                UPDATE injects
                SET inject_depends_duration=subquery.difference
                FROM (SELECT inject_id, inject_date, ex.exercise_start_date,
                             EXTRACT(EPOCH FROM (inject_date)) -  EXTRACT(EPOCH FROM (ex.exercise_start_date)) AS difference
                      FROM exercises ex
                               RIGHT JOIN events e on ex.exercise_id = e.event_exercise
                               RIGHT JOIN incidents i on e.event_id = i.incident_event
                               RIGHT JOIN injects inject on i.incident_id = inject.inject_incident
                      ORDER BY inject_date) AS subquery
                WHERE injects.inject_id = subquery.inject_id;
                """);
  }
}
