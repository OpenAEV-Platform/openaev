package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * {@code ioc_validations}: the OpenCTI IOC validation requests processed by OpenAEV (dissemination
 * assurance). One row per request and tenant, unique on the OpenCTI request id; the IOCs and the
 * (indicator, platform) pairs are JSON documents bounded by the request limits (200 indicators, 10
 * platforms).
 *
 * <p>The scenario, simulation and decider references are set to null when their row is deleted, so
 * the validation history survives a cleanup of the generated scenario.
 */
@Component
public class V6_20261003110000000__Add_ioc_validations extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS ioc_validations (
              ioc_validation_id VARCHAR(255) NOT NULL CONSTRAINT ioc_validations_pkey PRIMARY KEY,
              ioc_validation_external_id VARCHAR(255) NOT NULL,
              ioc_validation_name VARCHAR(255) NOT NULL,
              ioc_validation_description TEXT,
              ioc_validation_requested_by VARCHAR(255),
              ioc_validation_status VARCHAR(50) NOT NULL,
              ioc_validation_status_message TEXT,
              ioc_validation_requested_test_kinds JSONB NOT NULL DEFAULT '[]'::jsonb,
              ioc_validation_allowed_test_kinds JSONB NOT NULL DEFAULT '[]'::jsonb,
              ioc_validation_iocs JSONB NOT NULL DEFAULT '[]'::jsonb,
              ioc_validation_pairs JSONB NOT NULL DEFAULT '[]'::jsonb,
              ioc_validation_iocs_count INTEGER NOT NULL DEFAULT 0,
              ioc_validation_pairs_count INTEGER NOT NULL DEFAULT 0,
              ioc_validation_prevented_count INTEGER NOT NULL DEFAULT 0,
              ioc_validation_detected_count INTEGER NOT NULL DEFAULT 0,
              ioc_validation_missed_count INTEGER NOT NULL DEFAULT 0,
              ioc_validation_error_count INTEGER NOT NULL DEFAULT 0,
              ioc_validation_scenario VARCHAR(255)
                CONSTRAINT ioc_validations_scenario_fk
                REFERENCES scenarios (scenario_id) ON DELETE SET NULL,
              ioc_validation_simulation VARCHAR(255)
                CONSTRAINT ioc_validations_simulation_fk
                REFERENCES exercises (exercise_id) ON DELETE SET NULL,
              ioc_validation_opencti_url TEXT,
              ioc_validation_decided_by VARCHAR(255)
                CONSTRAINT ioc_validations_decided_by_fk
                REFERENCES users (user_id) ON DELETE SET NULL,
              ioc_validation_decided_by_name VARCHAR(255),
              ioc_validation_decided_at TIMESTAMP WITH TIME ZONE,
              ioc_validation_completed_at TIMESTAMP WITH TIME ZONE,
              ioc_validation_results_pushed_at TIMESTAMP WITH TIME ZONE,
              ioc_validation_opencti_synced_status VARCHAR(50),
              ioc_validation_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
              ioc_validation_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
              tenant_id VARCHAR(255) NOT NULL
                CONSTRAINT ioc_validations_tenant_fk
                REFERENCES tenants (tenant_id) ON DELETE CASCADE
          )
          """);
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS idx_ioc_validations_external_id_tenant_uq"
              + " ON ioc_validations (ioc_validation_external_id, tenant_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_tenant ON ioc_validations (tenant_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_status"
              + " ON ioc_validations (ioc_validation_status)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_scenario"
              + " ON ioc_validations (ioc_validation_scenario)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_simulation"
              + " ON ioc_validations (ioc_validation_simulation)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_decided_by"
              + " ON ioc_validations (ioc_validation_decided_by)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_created_at"
              + " ON ioc_validations (ioc_validation_created_at)");
    }
  }
}
