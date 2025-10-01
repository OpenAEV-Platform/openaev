package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_80__Inject_status extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Inject statuses
    select.execute("ALTER TABLE injects_statuses DROP status_async_ids");
    select.execute("ALTER TABLE injects_statuses RENAME status_date TO tracking_sent_date");
    select.execute(
        "ALTER TABLE injects_statuses RENAME status_execution TO tracking_total_execution_time");
    select.execute(
        "ALTER TABLE injects_statuses ALTER column tracking_total_execution_time type bigint");
    select.execute("ALTER TABLE injects_statuses ADD status_executions text;");
    select.execute("ALTER TABLE injects_statuses ADD tracking_ack_date timestamp;");
    select.execute("ALTER TABLE injects_statuses ADD tracking_end_date timestamp;");
    select.execute("ALTER TABLE injects_statuses ADD tracking_total_count int;");
    select.execute("ALTER TABLE injects_statuses ADD tracking_total_error int;");
    select.execute("ALTER TABLE injects_statuses ADD tracking_total_success int;");
    // Dry Inject statuses
    select.execute("ALTER TABLE dryinjects_statuses RENAME status_date TO tracking_sent_date");
    select.execute(
        "ALTER TABLE dryinjects_statuses RENAME status_execution TO tracking_total_execution_time");
    select.execute(
        "ALTER TABLE dryinjects_statuses ALTER column tracking_total_execution_time type bigint");
    select.execute("ALTER TABLE dryinjects_statuses ADD status_executions text;");
    select.execute("ALTER TABLE dryinjects_statuses ADD tracking_ack_date timestamp;");
    select.execute("ALTER TABLE dryinjects_statuses ADD tracking_end_date timestamp;");
    select.execute("ALTER TABLE dryinjects_statuses ADD tracking_total_count int;");
    select.execute("ALTER TABLE dryinjects_statuses ADD tracking_total_error int;");
    select.execute("ALTER TABLE dryinjects_statuses ADD tracking_total_success int;");
  }
}
