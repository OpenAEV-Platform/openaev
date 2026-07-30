package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Adds a remediation snapshot table keyed by step execution for attack-path execution detail. */
@Component
public class V6_20260729113000000__Add_attackpath_execution_remediation extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS attackpath_execution_remediation (
              attackpath_execution_remediation_id                text NOT NULL
                  CONSTRAINT attackpath_execution_remediation_pkey PRIMARY KEY,
              tenant_id                                          varchar(255) NOT NULL
                  CONSTRAINT attackpath_execution_remediation_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              attackpath_execution_remediation_step_id           varchar(255) NOT NULL
                  CONSTRAINT attackpath_execution_remediation_step_fk
                      REFERENCES steps (step_id) ON DELETE CASCADE,
              attackpath_execution_remediation_values            text NOT NULL,
              attackpath_execution_remediation_author_rule       author_enum NOT NULL,
              attackpath_execution_remediation_collector_type    varchar(255),
              attackpath_execution_remediation_security_platform varchar(255) NOT NULL
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_rem_tenant "
              + "ON attackpath_execution_remediation (tenant_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_rem_step "
              + "ON attackpath_execution_remediation (attackpath_execution_remediation_step_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_rem_step_collector_platform "
              + "ON attackpath_execution_remediation ("
              + "attackpath_execution_remediation_step_id, "
              + "COALESCE(attackpath_execution_remediation_collector_type, '0'), "
              + "attackpath_execution_remediation_security_platform)");
    }
  }
}
