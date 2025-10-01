package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_58__Reports extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        """
                CREATE TABLE reports (
                    report_id varchar(255) not null constraint reports_pkey primary key,
                    report_created_at timestamp not null default now(),
                    report_updated_at timestamp not null default now(),
                    report_name varchar(255) not null,
                    report_stats_definition bool not null default true,
                    report_stats_definition_score bool not null default true,
                    report_stats_data bool not null default true,
                    report_stats_results bool not null default true,
                    report_lessons_objectives bool not null default true,
                    report_lessons_stats bool not null default true,
                    report_lessons_details bool not null default true,
                    report_exercise varchar(255) not null constraint fk_report_exercise references exercises on delete cascade
                );
                CREATE INDEX idx_reports on reports (report_id);
                CREATE INDEX idx_report_exercise on reports (report_exercise);
        """);
  }
}
