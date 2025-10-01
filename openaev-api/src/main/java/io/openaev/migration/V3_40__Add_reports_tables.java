package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_40__Add_reports_tables extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute("DROP TABLE IF EXISTS reports;");

    // Create table
    select.execute(
        """
          CREATE TABLE reports (
            report_id UUID NOT NULL CONSTRAINT reports_pkey PRIMARY KEY,
            report_name VARCHAR(255) NOT NULL,
            report_global_observation TEXT,
            report_created_at TIMESTAMP DEFAULT now(),
            report_updated_at TIMESTAMP DEFAULT now()
          );
          CREATE INDEX idx_reports ON reports(report_id);

          CREATE TABLE reports_exercises (
            report_id UUID NOT NULL,
            exercise_id VARCHAR(255) NOT NULL,
            PRIMARY KEY (report_id, exercise_id),
            FOREIGN KEY (report_id) REFERENCES reports (report_id) ON DELETE CASCADE,
            FOREIGN KEY (exercise_id) REFERENCES exercises (exercise_id) ON DELETE CASCADE
          );
          CREATE INDEX idx_reports_exercises_report ON reports_exercises (report_id);
          CREATE INDEX idx_reports_exercises_exercise ON reports_exercises (exercise_id);

          CREATE TABLE report_informations (
            report_informations_id UUID NOT NULL CONSTRAINT report_informations_pkey PRIMARY KEY,
            report_id UUID NOT NULL,
            report_informations_type VARCHAR(255) NOT NULL,
            report_informations_display BOOLEAN DEFAULT FALSE,
            UNIQUE (report_id, report_informations_type),
            FOREIGN KEY (report_id) REFERENCES reports (report_id) ON DELETE CASCADE
          );
          CREATE INDEX idx_report_informations ON report_informations (report_id);

          CREATE TABLE report_inject_comment (
            report_id UUID NOT NULL,
            inject_id VARCHAR(255) NOT NULL,
            comment TEXT,
            PRIMARY KEY (report_id, inject_id),
            FOREIGN KEY (report_id) REFERENCES reports (report_id) ON DELETE CASCADE,
            FOREIGN KEY (inject_id) REFERENCES injects (inject_id) ON DELETE CASCADE
          );
          CREATE INDEX idx_report_inject_comment_inject ON report_inject_comment (report_id);
          CREATE INDEX idx_report_inject_comment_report ON report_inject_comment (inject_id);
     """);
  }
}
