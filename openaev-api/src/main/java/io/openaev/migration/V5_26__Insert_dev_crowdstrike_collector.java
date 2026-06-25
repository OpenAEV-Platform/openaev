package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Inserts a Crowdstrike collector in the default tenant for development purposes. Allows the
 * Remediation tab to be visible in ThreatArsenal without a real collector agent.
 *
 * <p>Uses the default tenant {@code 2cffad3a-0001-4078-b0e2-ef74274022c3}. Safe to run multiple
 * times (IF NOT EXISTS guards).
 */
@Component
public class V5_26__Insert_dev_crowdstrike_collector extends BaseJavaMigration {

  private static final String DEFAULT_TENANT_ID = "2cffad3a-0001-4078-b0e2-ef74274022c3";
  private static final String COLLECTOR_TYPE_ID = "dev-ct-0001-0000-0000-crowdstrike00";
  private static final String COLLECTOR_ID = "dev-col-0001-0000-0000-crowdstrike00";
  private static final String COLLECTOR_TYPE_NAME = "openaev_crowdstrike";
  private static final String COLLECTOR_NAME = "[DEV] Crowdstrike Falcon";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // 1. Insert the collector type (if not already present for this tenant)
      stmt.execute(
          """
          INSERT INTO collector_types (collector_type_id, collector_type_name, tenant_id)
          VALUES ('%s', '%s', '%s')
          ON CONFLICT DO NOTHING;
          """
              .formatted(COLLECTOR_TYPE_ID, COLLECTOR_TYPE_NAME, DEFAULT_TENANT_ID));

      // 2. Insert the collector itself referencing the type
      stmt.execute(
          """
          INSERT INTO collectors (
              collector_id,
              collector_name,
              collector_type,
              collector_type_id,
              collector_period,
              collector_external,
              collector_created_at,
              collector_updated_at,
              tenant_id
          )
          VALUES (
              '%s',
              '%s',
              '%s',
              '%s',
              0,
              false,
              now(),
              now(),
              '%s'
          )
          ON CONFLICT DO NOTHING;
          """
              .formatted(
                  COLLECTOR_ID,
                  COLLECTOR_NAME,
                  COLLECTOR_TYPE_NAME,
                  COLLECTOR_TYPE_ID,
                  DEFAULT_TENANT_ID));
    }
  }
}
