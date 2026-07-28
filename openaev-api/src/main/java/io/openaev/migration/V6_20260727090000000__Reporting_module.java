package io.openaev.migration;

import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Reporting module: report templates (reportings), their executions (reporting_generations) and
 * recurring schedules (reporting_schedules + reporting_schedules_users recipients).
 *
 * <p>Also best-effort migrates the legacy per-simulation reports feature (reports,
 * reports_exercises, report_informations, report_inject_comment) into SIMULATION-scoped reportings:
 * displayed legacy sections map to the closest new module type, the global observation and inject
 * comments become CUSTOM_MARKDOWN modules, and PLAYER_SURVEYS is dropped (no equivalent). The
 * legacy tables are dropped afterwards.
 */
@Component
public class V6_20260727090000000__Reporting_module extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      createReportingTables(statement);
      if (legacyReportsPresent(statement)) {
        migrateLegacyReports(statement);
      }
      dropLegacyTables(statement);
    }
  }

  private void createReportingTables(Statement statement) throws Exception {
    // -- Report templates --
    statement.executeUpdate(
        """
        CREATE TABLE IF NOT EXISTS reportings (
            reporting_id varchar(255) NOT NULL,
            reporting_name varchar(255) NOT NULL,
            reporting_description text,
            reporting_context_type varchar(255) NOT NULL,
            reporting_context_id varchar(255),
            reporting_modules jsonb NOT NULL DEFAULT '[]'::jsonb,
            reporting_branding jsonb,
            reporting_default_format varchar(255) NOT NULL DEFAULT 'PDF',
            reporting_time_range varchar(255) NOT NULL DEFAULT 'LAST_30_DAYS',
            reporting_created_at timestamp NOT NULL DEFAULT now(),
            reporting_updated_at timestamp NOT NULL DEFAULT now(),
            tenant_id varchar(255) NOT NULL,
            PRIMARY KEY (reporting_id),
            CONSTRAINT fk_reportings_tenant
                FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_reportings_tenant ON reportings(tenant_id);
        CREATE INDEX IF NOT EXISTS idx_reportings_context
            ON reportings(reporting_context_type, reporting_context_id);
        """);

    // -- Generations --
    statement.executeUpdate(
        """
        CREATE TABLE IF NOT EXISTS reporting_generations (
            reporting_generation_id varchar(255) NOT NULL,
            reporting_id varchar(255) NOT NULL,
            reporting_generation_status varchar(255) NOT NULL DEFAULT 'PENDING',
            reporting_generation_format varchar(255) NOT NULL,
            reporting_generation_trigger varchar(255) NOT NULL DEFAULT 'MANUAL',
            document_id varchar(255),
            reporting_generation_error text,
            reporting_generation_created_at timestamp NOT NULL DEFAULT now(),
            reporting_generation_completed_at timestamp,
            tenant_id varchar(255) NOT NULL,
            PRIMARY KEY (reporting_generation_id),
            CONSTRAINT fk_reporting_generations_reporting
                FOREIGN KEY (reporting_id) REFERENCES reportings(reporting_id) ON DELETE CASCADE,
            CONSTRAINT fk_reporting_generations_document
                FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE SET NULL,
            CONSTRAINT fk_reporting_generations_tenant
                FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_reporting_generations_tenant
            ON reporting_generations(tenant_id);
        CREATE INDEX IF NOT EXISTS idx_reporting_generations_reporting
            ON reporting_generations(reporting_id);
        CREATE INDEX IF NOT EXISTS idx_reporting_generations_document
            ON reporting_generations(document_id);
        CREATE INDEX IF NOT EXISTS idx_reporting_generations_status
            ON reporting_generations(reporting_generation_status);
        """);

    // -- Schedules --
    statement.executeUpdate(
        """
        CREATE TABLE IF NOT EXISTS reporting_schedules (
            reporting_schedule_id varchar(255) NOT NULL,
            reporting_id varchar(255) NOT NULL,
            reporting_schedule_name varchar(255),
            reporting_schedule_period varchar(255) NOT NULL,
            reporting_schedule_time varchar(255),
            reporting_schedule_format varchar(255) NOT NULL DEFAULT 'PDF',
            reporting_schedule_enabled bool NOT NULL DEFAULT true,
            user_id varchar(255) NOT NULL,
            reporting_schedule_recipient_emails jsonb NOT NULL DEFAULT '[]'::jsonb,
            reporting_schedule_last_run_at timestamp,
            reporting_schedule_created_at timestamp NOT NULL DEFAULT now(),
            reporting_schedule_updated_at timestamp NOT NULL DEFAULT now(),
            tenant_id varchar(255) NOT NULL,
            PRIMARY KEY (reporting_schedule_id),
            CONSTRAINT fk_reporting_schedules_reporting
                FOREIGN KEY (reporting_id) REFERENCES reportings(reporting_id) ON DELETE CASCADE,
            CONSTRAINT fk_reporting_schedules_user
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
            CONSTRAINT fk_reporting_schedules_tenant
                FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_reporting_schedules_tenant
            ON reporting_schedules(tenant_id);
        CREATE INDEX IF NOT EXISTS idx_reporting_schedules_reporting
            ON reporting_schedules(reporting_id);
        CREATE INDEX IF NOT EXISTS idx_reporting_schedules_user
            ON reporting_schedules(user_id);
        CREATE TABLE IF NOT EXISTS reporting_schedules_users (
            reporting_schedule_id varchar(255) NOT NULL,
            user_id varchar(255) NOT NULL,
            PRIMARY KEY (reporting_schedule_id, user_id),
            CONSTRAINT fk_rsu_schedule
                FOREIGN KEY (reporting_schedule_id)
                REFERENCES reporting_schedules(reporting_schedule_id) ON DELETE CASCADE,
            CONSTRAINT fk_rsu_user
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_reporting_schedules_users_user
            ON reporting_schedules_users(user_id);
        """);
  }

  private boolean legacyReportsPresent(Statement statement) throws Exception {
    try (ResultSet rs =
        statement.executeQuery(
            "SELECT to_regclass('reports') IS NOT NULL"
                + " AND to_regclass('reports_exercises') IS NOT NULL AS present")) {
      return rs.next() && rs.getBoolean("present");
    }
  }

  /**
   * Best-effort import of legacy reports as SIMULATION reportings. Reports without an exercise link
   * are skipped (no tenant can be resolved); a report linked to several exercises keeps only one
   * (same primary key, ON CONFLICT DO NOTHING).
   */
  private void migrateLegacyReports(Statement statement) throws Exception {
    statement.executeUpdate(
        """
        INSERT INTO reportings (reporting_id, reporting_name, reporting_context_type,
                                reporting_context_id, reporting_modules, reporting_branding,
                                reporting_default_format, reporting_time_range,
                                reporting_created_at, reporting_updated_at, tenant_id)
        SELECT r.report_id::varchar,
               r.report_name,
               'SIMULATION',
               re.exercise_id,
               jsonb_build_array(jsonb_build_object('module_type', 'COVER'))
               || CASE WHEN EXISTS (
                      SELECT 1 FROM report_informations ri
                      WHERE ri.report_id = r.report_id
                        AND ri.report_informations_type = 'MAIN_INFORMATION'
                        AND ri.report_informations_display)
                  THEN jsonb_build_array(jsonb_build_object('module_type', 'EXECUTIVE_SUMMARY'))
                  ELSE '[]'::jsonb END
               || CASE WHEN EXISTS (
                      SELECT 1 FROM report_informations ri
                      WHERE ri.report_id = r.report_id
                        AND ri.report_informations_type = 'EXERCISE_DETAILS'
                        AND ri.report_informations_display)
                  THEN jsonb_build_array(jsonb_build_object('module_type', 'SUBJECT_DETAILS'))
                  ELSE '[]'::jsonb END
               || CASE WHEN EXISTS (
                      SELECT 1 FROM report_informations ri
                      WHERE ri.report_id = r.report_id
                        AND ri.report_informations_type = 'INJECT_RESULT'
                        AND ri.report_informations_display)
                  THEN jsonb_build_array(jsonb_build_object('module_type', 'RESULTS_BREAKDOWN'))
                  ELSE '[]'::jsonb END
               || CASE WHEN EXISTS (
                      SELECT 1 FROM report_informations ri
                      WHERE ri.report_id = r.report_id
                        AND ri.report_informations_type = 'SCORE_DETAILS'
                        AND ri.report_informations_display)
                  THEN jsonb_build_array(jsonb_build_object('module_type', 'SCORE_TRENDS'))
                  ELSE '[]'::jsonb END
               || CASE WHEN r.report_global_observation IS NOT NULL
                        AND r.report_global_observation <> ''
                        AND EXISTS (
                            SELECT 1 FROM report_informations ri
                            WHERE ri.report_id = r.report_id
                              AND ri.report_informations_type = 'GLOBAL_OBSERVATION'
                              AND ri.report_informations_display)
                  THEN jsonb_build_array(jsonb_build_object(
                      'module_type', 'CUSTOM_MARKDOWN',
                      'module_title', 'Global observation',
                      'module_config',
                          jsonb_build_object('content', r.report_global_observation)))
                  ELSE '[]'::jsonb END
               || COALESCE(
                   (SELECT jsonb_build_array(jsonb_build_object(
                        'module_type', 'CUSTOM_MARKDOWN',
                        'module_title', 'Inject comments',
                        'module_config', jsonb_build_object('content',
                            string_agg(
                                '**' || i.inject_title || '**' || chr(10) || chr(10)
                                    || ric.comment,
                                chr(10) || chr(10) ORDER BY i.inject_title))))
                    FROM report_inject_comment ric
                    JOIN injects i ON i.inject_id = ric.inject_id
                    WHERE ric.report_id = r.report_id
                      AND ric.comment IS NOT NULL AND ric.comment <> ''),
                   '[]'::jsonb),
               NULL,
               'PDF',
               'ALL_TIME',
               COALESCE(r.report_created_at, now()),
               COALESCE(r.report_updated_at, now()),
               e.tenant_id
        FROM reports r
        JOIN reports_exercises re ON re.report_id = r.report_id
        JOIN exercises e ON e.exercise_id = re.exercise_id
        ON CONFLICT (reporting_id) DO NOTHING;
        """);
  }

  private void dropLegacyTables(Statement statement) throws Exception {
    statement.executeUpdate(
        """
        DROP TABLE IF EXISTS report_inject_comment;
        DROP TABLE IF EXISTS report_informations;
        DROP TABLE IF EXISTS reports_exercises;
        DROP TABLE IF EXISTS reports;
        """);
  }
}

// -- ROLLBACK --
// DROP TABLE IF EXISTS reporting_schedules_users;
// DROP TABLE IF EXISTS reporting_schedules;
// DROP TABLE IF EXISTS reporting_generations;
// DROP TABLE IF EXISTS reportings;
