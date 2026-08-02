package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Creates the built-in phishing capability tables: reusable landing pages and email templates (new
 * "Components"), plus a per-recipient tracking result table. All tenant-scoped.
 */
@Component
public class V6_20260805130000000__Add_phishing extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // -- Landing pages --
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS phishing_landing_pages (
              phishing_landing_page_id                    varchar(255) NOT NULL
                  CONSTRAINT phishing_landing_pages_pkey PRIMARY KEY,
              tenant_id                                   varchar(255) NOT NULL
                  CONSTRAINT phishing_landing_pages_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              phishing_landing_page_name                  varchar(255) NOT NULL,
              phishing_landing_page_description           text,
              phishing_landing_page_html                  text,
              phishing_landing_page_css                   text,
              phishing_landing_page_capture_submitted_data boolean NOT NULL DEFAULT true,
              phishing_landing_page_capture_passwords     boolean NOT NULL DEFAULT true,
              phishing_landing_page_redirect_url          text,
              phishing_landing_page_primary_color_dark    varchar(255),
              phishing_landing_page_primary_color_light   varchar(255),
              phishing_landing_page_logo_dark             varchar(255)
                  CONSTRAINT phishing_landing_pages_logo_dark_fk
                      REFERENCES documents (document_id) ON DELETE SET NULL,
              phishing_landing_page_logo_light            varchar(255)
                  CONSTRAINT phishing_landing_pages_logo_light_fk
                      REFERENCES documents (document_id) ON DELETE SET NULL,
              phishing_landing_page_created_at            timestamp NOT NULL DEFAULT now(),
              phishing_landing_page_updated_at            timestamp NOT NULL DEFAULT now()
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_phishing_landing_pages_tenant "
              + "ON phishing_landing_pages (tenant_id)");

      // -- Email templates --
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS phishing_email_templates (
              phishing_email_template_id                  varchar(255) NOT NULL
                  CONSTRAINT phishing_email_templates_pkey PRIMARY KEY,
              tenant_id                                   varchar(255) NOT NULL
                  CONSTRAINT phishing_email_templates_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              phishing_email_template_name                varchar(255) NOT NULL,
              phishing_email_template_description         text,
              phishing_email_template_subject             varchar(255) NOT NULL,
              phishing_email_template_html_body           text,
              phishing_email_template_text_body           text,
              phishing_email_template_from_name           varchar(255),
              phishing_email_template_from_email          varchar(255),
              phishing_email_template_add_tracking_pixel  boolean NOT NULL DEFAULT true,
              phishing_email_template_created_at          timestamp NOT NULL DEFAULT now(),
              phishing_email_template_updated_at          timestamp NOT NULL DEFAULT now()
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_phishing_email_templates_tenant "
              + "ON phishing_email_templates (tenant_id)");

      // -- Per-recipient tracking results --
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS phishing_results (
              phishing_result_id            varchar(255) NOT NULL
                  CONSTRAINT phishing_results_pkey PRIMARY KEY,
              tenant_id                     varchar(255) NOT NULL
                  CONSTRAINT phishing_results_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              phishing_result_token         varchar(255) NOT NULL
                  CONSTRAINT phishing_results_token_unique UNIQUE,
              phishing_result_inject        varchar(255)
                  CONSTRAINT phishing_results_inject_fk
                      REFERENCES injects (inject_id) ON DELETE CASCADE,
              phishing_result_landing_page  varchar(255)
                  CONSTRAINT phishing_results_landing_page_fk
                      REFERENCES phishing_landing_pages (phishing_landing_page_id) ON DELETE SET NULL,
              phishing_result_user          varchar(255)
                  CONSTRAINT phishing_results_user_fk
                      REFERENCES users (user_id) ON DELETE CASCADE,
              phishing_result_team          varchar(255)
                  CONSTRAINT phishing_results_team_fk
                      REFERENCES teams (team_id) ON DELETE SET NULL,
              phishing_result_sent_at       timestamp,
              phishing_result_opened_at     timestamp,
              phishing_result_clicked_at    timestamp,
              phishing_result_submitted_at  timestamp,
              phishing_result_ip            varchar(255),
              phishing_result_user_agent    text,
              phishing_result_finding       varchar(255)
                  CONSTRAINT phishing_results_finding_fk
                      REFERENCES findings (finding_id) ON DELETE SET NULL,
              phishing_result_created_at    timestamp NOT NULL DEFAULT now(),
              phishing_result_updated_at    timestamp NOT NULL DEFAULT now()
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_phishing_results_tenant "
              + "ON phishing_results (tenant_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_phishing_results_token "
              + "ON phishing_results (phishing_result_token)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_phishing_results_inject "
              + "ON phishing_results (phishing_result_inject)");
    }
  }
}
