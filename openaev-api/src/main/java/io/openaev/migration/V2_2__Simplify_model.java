package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_2__Simplify_model extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute("DROP TABLE IF EXISTS doctrine_migration_versions;");
    // 01. Outcome - clean the table, redirect to inject
    //noinspection SqlWithoutWhere
    select.execute("DELETE FROM outcomes;");
    select.execute("ALTER TABLE outcomes DROP COLUMN outcome_incident;");
    select.execute("ALTER TABLE outcomes ADD outcome_inject varchar(255);");
    select.execute(
        "ALTER TABLE outcomes ADD constraint fk_outcome_inject "
            + "foreign key (outcome_inject) references injects on delete cascade;");

    // 02. Injects, redirect from incident to exercise
    // 02.01 - Create the new reference
    select.execute("ALTER TABLE injects ADD inject_exercise varchar(255);");
    select.execute(
        "ALTER TABLE injects ADD constraint fk_inject_exercise "
            + "foreign key (inject_exercise) references exercises on delete cascade;");
    // 02.02 - Migrate the injects.
    select.executeUpdate(
        """
                UPDATE injects
                SET inject_exercise=subquery.event_exercise
                FROM (SELECT inject.inject_id, e.event_exercise
                      FROM injects inject
                      RIGHT JOIN incidents inc on inc.incident_id = inject.inject_incident
                      RIGHT JOIN events e on inc.incident_event = e.event_id) AS subquery
                WHERE injects.inject_id = subquery.inject_id;
                """);
    // 02.03 - Clean the table
    select.execute("ALTER TABLE injects DROP COLUMN inject_incident;");

    // 03. Drop incidents / events
    select.execute("DROP TABLE planificateurs_events;");
    select.execute("DROP TABLE incidents_subobjectives;");
    select.execute("DROP TABLE subobjectives;");
    select.execute("DROP TABLE incidents;");
    select.execute("DROP TABLE incident_types;");
    select.execute("DROP TABLE events;");

    // 04. Drop audiences
    // 04.01 - Redirect subaudiances to exercise
    select.execute("ALTER TABLE subaudiences ADD audience_exercise varchar(255);");
    //noinspection SqlResolve
    select.execute(
        "ALTER TABLE subaudiences ADD constraint fk_audience_exercise "
            + "foreign key (audience_exercise) references exercises on delete cascade;");
    //noinspection SqlResolve
    select.executeUpdate(
        """
                UPDATE subaudiences
                SET audience_exercise=subquery.audience_exercise
                FROM (SELECT sub.subaudience_id, au.audience_exercise
                      FROM subaudiences sub
                      RIGHT JOIN audiences au on au.audience_id = sub.subaudience_audience
                      ) AS subquery
                WHERE subaudiences.subaudience_id = subquery.subaudience_id;
                """);
    select.execute("ALTER TABLE subaudiences DROP COLUMN subaudience_audience;");
    // 04.02 - Redirect comchecks
    //noinspection SqlWithoutWhere
    select.execute("DELETE FROM comchecks;");
    select.execute("ALTER TABLE comchecks DROP COLUMN comcheck_audience;");
    select.execute("ALTER TABLE comchecks ADD comcheck_audience varchar(255);");
    select.execute(
        "ALTER TABLE comchecks ADD constraint fk_comcheck_audience "
            + "foreign key (comcheck_audience) references subaudiences on delete cascade;");
    // 04.03 - Delete tables
    select.execute("DROP TABLE planificateurs_audiences;");
    select.execute("DROP TABLE injects_audiences;");
    select.execute("DROP TABLE audiences;");
    // 04.04 - Rename subaudience
    select.execute("ALTER TABLE subaudiences RENAME COLUMN subaudience_id TO audience_id;");
    select.execute("ALTER TABLE subaudiences RENAME COLUMN subaudience_name TO audience_name;");
    select.execute(
        "ALTER TABLE subaudiences RENAME COLUMN subaudience_enabled TO audience_enabled;");
    select.execute("ALTER TABLE subaudiences RENAME TO audiences;");
    select.execute("ALTER TABLE injects_subaudiences RENAME COLUMN subaudience_id TO audience_id;");
    select.execute("ALTER TABLE injects_subaudiences RENAME TO injects_audiences;");
    select.execute("ALTER TABLE users_subaudiences RENAME COLUMN subaudience_id TO audience_id;");
    select.execute("ALTER TABLE users_subaudiences RENAME TO users_audiences;");

    // 04. Drop files
    select.execute("ALTER TABLE exercises DROP COLUMN exercise_image;");
    select.execute("ALTER TABLE exercises ADD exercise_image varchar(255);");
    select.execute(
        "ALTER TABLE exercises ADD constraint fk_exercise_image "
            + "foreign key (exercise_image) references documents on delete cascade;");
    select.execute("DROP TABLE files;");
    select.execute("DROP TABLE documents_exercises;");
  }
}
