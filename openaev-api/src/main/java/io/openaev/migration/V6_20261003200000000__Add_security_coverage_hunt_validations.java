package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Outbox of the OpenCTI hunt validation loop: one row per emulated (inject, ATT&CK technique,
 * security platform) triple. The unique key is what makes the validation idempotent across security
 * coverage job reruns; the retry columns let the delivery job survive an OpenCTI outage without
 * re-planning anything.
 */
@Component
public class V6_20261003200000000__Add_security_coverage_hunt_validations
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS security_coverage_hunt_validations (
              security_coverage_hunt_validation_id VARCHAR(255) NOT NULL
                  CONSTRAINT security_coverage_hunt_validations_pkey PRIMARY KEY,
              security_coverage_hunt_validation_inject_id VARCHAR(255) NOT NULL
                  CONSTRAINT security_coverage_hunt_validations_inject_fk
                  REFERENCES injects (inject_id) ON DELETE CASCADE,
              security_coverage_hunt_validation_security_platform_id VARCHAR(255) NOT NULL
                  CONSTRAINT security_coverage_hunt_validations_security_platform_fk
                  REFERENCES assets (asset_id) ON DELETE CASCADE,
              security_coverage_hunt_validation_security_platform_name VARCHAR(255) NOT NULL,
              security_coverage_hunt_validation_technique_id VARCHAR(255) NOT NULL,
              security_coverage_hunt_validation_coverage_external_id VARCHAR(255) NOT NULL,
              security_coverage_hunt_validation_window_start TIMESTAMP WITH TIME ZONE NOT NULL,
              security_coverage_hunt_validation_window_end TIMESTAMP WITH TIME ZONE NOT NULL,
              security_coverage_hunt_validation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
                  CONSTRAINT security_coverage_hunt_validations_status_check
                  CHECK (security_coverage_hunt_validation_status IN ('PENDING', 'VALIDATED', 'FAILED')),
              security_coverage_hunt_validation_attempts INTEGER NOT NULL DEFAULT 0,
              security_coverage_hunt_validation_next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
              security_coverage_hunt_validation_last_error TEXT,
              security_coverage_hunt_validation_hunts_count INTEGER,
              security_coverage_hunt_validation_runs_count INTEGER,
              security_coverage_hunt_validation_validated_at TIMESTAMP WITH TIME ZONE,
              security_coverage_hunt_validation_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
              security_coverage_hunt_validation_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
              tenant_id VARCHAR(255) NOT NULL
                  CONSTRAINT security_coverage_hunt_validations_tenant_fk
                  REFERENCES tenants (tenant_id) ON DELETE CASCADE
          )
          """);

      // The idempotency key. Its leading column also serves the inject foreign key.
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS idx_security_coverage_hunt_validations_key_tenant_uq"
              + " ON security_coverage_hunt_validations (security_coverage_hunt_validation_inject_id,"
              + " security_coverage_hunt_validation_technique_id,"
              + " security_coverage_hunt_validation_security_platform_id, tenant_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_security_coverage_hunt_validations_security_platform"
              + " ON security_coverage_hunt_validations"
              + " (security_coverage_hunt_validation_security_platform_id)");
      // Polling cursor of the delivery job, which always reads within one tenant: due PENDING rows,
      // oldest first. Its leading column also serves the tenant foreign key.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_security_coverage_hunt_validations_tenant_due"
              + " ON security_coverage_hunt_validations (tenant_id,"
              + " security_coverage_hunt_validation_status,"
              + " security_coverage_hunt_validation_next_attempt_at)");
    }
  }
}
