package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_46__Expectation_upgrade extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // drop unneeded
    select.execute("ALTER TABLE injects_expectations_executions DROP inject_expectation_id;");
    // Rename old fields
    select.execute(
        "ALTER TABLE injects_expectations_executions RENAME COLUMN expectation_execution_id TO inject_expectation_id;");
    select.execute(
        "ALTER TABLE injects_expectations_executions RENAME COLUMN expectation_execution_created_at TO inject_expectation_created_at;");
    select.execute(
        "ALTER TABLE injects_expectations_executions RENAME COLUMN expectation_execution_updated_at TO inject_expectation_updated_at;");
    select.execute(
        "ALTER TABLE injects_expectations_executions RENAME COLUMN expectation_execution_result TO inject_expectation_result;");
    // Add missing fields
    select.execute(
        "ALTER TABLE injects_expectations_executions ADD inject_expectation_type varchar(255) not null;");
    select.execute("ALTER TABLE injects_expectations_executions ADD inject_expectation_score int;");
    select.execute("ALTER TABLE injects_expectations_executions ADD article_id varchar(255);");
    select.execute(
        "ALTER TABLE injects_expectations_executions ADD constraint fk_article foreign key (article_id) references articles on delete cascade;");
    select.execute("ALTER TABLE injects_expectations_executions ADD challenge_id varchar(255);");
    select.execute(
        "ALTER TABLE injects_expectations_executions ADD constraint fk_challenge foreign key (challenge_id) references challenges on delete cascade;");
    // Drop old table
    select.execute("DROP TABLE injects_expectations;");
    select.execute("DROP INDEX idx_injects_expectations_executions;");

    // Rename
    select.execute("ALTER TABLE injects_expectations_executions RENAME TO injects_expectations;");
  }
}
