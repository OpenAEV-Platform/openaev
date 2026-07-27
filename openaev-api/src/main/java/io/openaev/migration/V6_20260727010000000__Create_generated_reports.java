package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Creates the {@code generated_reports} table backing the structured PDF "Reports" feature
 * (Technical / Executive fixed templates, scoped to a single simulation, a scenario across all its
 * runs, or globally across every simulation). The PDF binary itself is stored via the existing
 * {@code documents} table / MinIO storage; this table only tracks generation metadata and status
 * (PENDING/RUNNING/COMPLETED/FAILED) for traceability and history.
 *
 * <p>Scope is determined by which of {@code generated_report_exercise} / {@code
 * generated_report_scenario} is set: both null means a Global report, only exercise set means a
 * Simulation report, only scenario set means a Scenario report (aggregating every run of that
 * scenario within the requested comparison window).
 */
@Component
public class V6_20260727010000000__Create_generated_reports extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE generated_reports (
            generated_report_id varchar(255) not null
              constraint generated_reports_pkey primary key,
            generated_report_template varchar(255) not null,
            generated_report_status varchar(255) not null default 'PENDING',
            generated_report_error_message text,
            generated_report_trigger_source varchar(255) not null default 'MANUAL',
            generated_report_label text,
            generated_report_created_at timestamp not null default now(),
            generated_report_updated_at timestamp not null default now(),
            generated_report_exercise varchar(255)
              constraint generated_reports_exercise_fk
                references exercises
                on delete cascade,
            generated_report_scenario varchar(255)
              constraint generated_reports_scenario_fk
                references scenarios
                on delete cascade,
            generated_report_document varchar(255)
              constraint generated_reports_document_fk
                references documents
                on delete set null,
            generated_report_created_by varchar(255)
              constraint generated_reports_created_by_fk
                references users
                on delete set null,
            tenant_id varchar(255) not null
              constraint generated_reports_tenant_fk
                references tenants
                on delete cascade
          );
          CREATE INDEX idx_generated_reports_exercise ON generated_reports(generated_report_exercise);
          CREATE INDEX idx_generated_reports_scenario ON generated_reports(generated_report_scenario);
          CREATE INDEX idx_generated_reports_tenant ON generated_reports(tenant_id);
          """);
    }
  }
}
