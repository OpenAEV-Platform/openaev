package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Reassigns new UUID primary keys to CrowdStrike/SentinelOne agents whose {@code agent_id} was
 * previously set to their {@code agent_external_reference}.
 *
 * <p>This caused cross-tenant PK collisions when multiple tenants synced the same external
 * platform. After this migration, {@code agent_id} is a random UUID and {@code
 * agent_external_reference} remains the external device ID used for API callbacks.
 */
@Component
public class V6_20260701180000000__Reassign_agent_ids_for_external_executors
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // Reassign UUID to agents where agent_id equals agent_external_reference
      // (i.e. CS/S1 agents that used external reference as PK).
      // gen_random_uuid() is available in PostgreSQL 13+.
      stmt.execute(
          """
          UPDATE agents
          SET agent_id = gen_random_uuid()::text
          WHERE agent_external_reference IS NOT NULL
            AND agent_id = agent_external_reference
          """);
    }
  }
}
