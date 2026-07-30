package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds collector-result snapshot rows for attack-path execution detail.
 *
 * <p>Each row links one execution to one collector line (source/type/status/date/metadata), so the
 * execution detail renders frozen collector data instead of resolving mutable live expectations.
 * Additive and idempotent.
 */
@Component
public class V6_20260730130000000__Add_attackpath_execution_collector extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS attackpath_execution_collector (
              attackpath_execution_collector_id text NOT NULL
                  CONSTRAINT attackpath_execution_collector_pkey PRIMARY KEY,
              tenant_id varchar(255) NOT NULL
                  CONSTRAINT attackpath_execution_collector_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              attackpath_execution_collector_simulation_id varchar(255) NOT NULL,
              attackpath_execution_id text NOT NULL
                  CONSTRAINT attackpath_execution_collector_execution_fk
                      REFERENCES attackpath_execution (attackpath_execution_id) ON DELETE CASCADE,
              attackpath_execution_collector_expectation_type text NOT NULL,
              attackpath_execution_collector_source_id varchar(255),
              attackpath_execution_collector_source_type text,
              attackpath_execution_collector_source_name text,
              attackpath_execution_collector_source_asset_id varchar(255),
              attackpath_execution_collector_result_status_label text NOT NULL,
              attackpath_execution_collector_detection_time text,
              attackpath_execution_collector_alerts jsonb,
              attackpath_execution_collector_result_score double precision,
              attackpath_execution_collector_result_date text
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_collector_tenant_sim "
              + "ON attackpath_execution_collector (tenant_id, attackpath_execution_collector_simulation_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_collector_tenant_execution "
              + "ON attackpath_execution_collector (tenant_id, attackpath_execution_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_collector_sim_type "
              + "ON attackpath_execution_collector (tenant_id, attackpath_execution_collector_simulation_id, attackpath_execution_collector_expectation_type);");
    }
  }
}
