package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_21__Comcheck_refactor extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Comchecks
    select.execute("ALTER TABLE comchecks DROP comcheck_audience;");
    select.execute("ALTER TABLE comchecks ADD comcheck_state varchar(256);");
    select.execute("ALTER TABLE comchecks ADD comcheck_subject varchar(256);");
    select.execute("ALTER TABLE comchecks ADD comcheck_message text;");
    select.execute("ALTER TABLE comchecks ADD comcheck_signature text;");
    // Comchecks statuses
    select.execute("ALTER TABLE comchecks_statuses DROP status_state;");
    select.execute("ALTER TABLE comchecks_statuses DROP status_last_update;");
    select.execute("ALTER TABLE comchecks_statuses ADD status_sent_date timestamp;");
    select.execute("ALTER TABLE comchecks_statuses ADD status_receive_date timestamp;");
    select.execute("ALTER TABLE comchecks_statuses ADD status_sent_retry int;");
    // Inject and dry reporting
    select.execute(
        "ALTER TABLE injects_statuses RENAME COLUMN status_message TO status_reporting;");
    select.execute(
        "ALTER TABLE dryinjects_statuses RENAME COLUMN status_message TO status_reporting;");
  }
}
