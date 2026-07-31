package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260731130000000__Add_finding_triage extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              DO $$
              BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'finding_triage_status') THEN
                      CREATE TYPE finding_triage_status AS ENUM ('UNTRIAGED', 'CONFIRMED', 'FALSE_POSITIVE', 'RISK_ACCEPTED');
                END IF;
               END;
              $$;
              """);

      statement.execute(
          """
              CREATE TABLE finding_triages (
                  finding_triage_id varchar(255) NOT NULL CONSTRAINT finding_triages_pkey PRIMARY KEY,
                  finding_triage_finding_id VARCHAR(255) NOT NULL CONSTRAINT finding_triage_finding_id_fk REFERENCES findings(finding_id) ON DELETE CASCADE,
                  finding_triage_status finding_triage_status NOT NULL DEFAULT 'UNTRIAGED',
                  tenant_id VARCHAR(255) NOT NULL CONSTRAINT finding_triage_tenant_id_fk REFERENCES tenants(tenant_id) ON DELETE CASCADE,
                  finding_triage_created_at TIMESTAMP DEFAULT now(),
                  finding_triage_updated_at TIMESTAMP DEFAULT now()
              );
              CREATE UNIQUE INDEX idx_finding_triages_finding_id ON finding_triages (finding_triage_finding_id);
              CREATE INDEX idx_finding_triages_tenant_id ON finding_triages (tenant_id);
              """);

      statement.execute(
          """
              CREATE TABLE finding_triage_histories (
                  finding_triage_history_id varchar(255) NOT NULL CONSTRAINT finding_triage_histories_pkey PRIMARY KEY,
                  finding_triage_history_finding_id VARCHAR(255) NOT NULL CONSTRAINT finding_triage_history_finding_id_fk REFERENCES findings(finding_id) ON DELETE CASCADE,
                  finding_triage_history_from_status finding_triage_status NOT NULL,
                  finding_triage_history_to_status finding_triage_status NOT NULL,
                  -- 4000 reuses the baseline set by finding_comment_content
                  -- (V6_20260730140000000__Add_finding_comments); 10 is the product-mandated minimum
                  -- so a justification carries real rationale, not a placeholder.
                  finding_triage_history_justification TEXT NOT NULL
                      CONSTRAINT finding_triage_history_justification_length_chk
                      CHECK (char_length(finding_triage_history_justification) >= 10
                          AND char_length(finding_triage_history_justification) <= 4000),
                  -- Nullable: NULL = "System" (automatic re-detection reset), never a real user.
                  finding_triage_history_actor_id VARCHAR(255) CONSTRAINT finding_triage_history_actor_id_fk REFERENCES users(user_id) ON DELETE SET NULL,
                  tenant_id VARCHAR(255) NOT NULL CONSTRAINT finding_triage_history_tenant_id_fk REFERENCES tenants(tenant_id) ON DELETE CASCADE,
                  finding_triage_history_created_at TIMESTAMP DEFAULT now()
              );
              CREATE INDEX idx_finding_triage_histories_finding_id ON finding_triage_histories (finding_triage_history_finding_id);
              CREATE INDEX idx_finding_triage_histories_tenant_id ON finding_triage_histories (tenant_id);
              """);

      // Backfill: every pre-existing finding gets an UNTRIAGED row. No history row for the
      // backfill itself - only real user/system-triggered actions create history rows.
      statement.execute(
          """
              INSERT INTO finding_triages
                (finding_triage_id, finding_triage_finding_id, finding_triage_status, tenant_id,
                 finding_triage_created_at, finding_triage_updated_at)
              SELECT gen_random_uuid(), f.finding_id, 'UNTRIAGED', f.tenant_id, now(), now()
              FROM findings f;
              """);
    }
  }
}
