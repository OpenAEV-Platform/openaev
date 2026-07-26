package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Releases security platforms still locked by collectors whose Integration Manager deployment was
 * deleted before the connector-instance cascade delete existed (PR #5809): deleting the instance
 * back then left the collector row behind, and a platform with a live entry in {@code
 * security_platform_collectors} stays read-only in the UI forever.
 *
 * <p>A collector deployed through the Integration Manager is identified by a {@code
 * connector_instance_configurations} row with key {@code COLLECTOR_ID} holding the collector id -
 * the same join {@code AbstractConnectorService#deleteOwningConnectorInstance} uses. Orphaned
 * collectors no longer have that row, but neither do manually deployed (docker-compose) ones, so a
 * 7-day heartbeat guard protects any collector that is still pinging.
 *
 * <p>Only the security platform link is cleared - the collector row itself is kept (it may still be
 * referenced by payloads / contracts, and admins can delete it explicitly). The unlink is
 * self-healing: if such a collector ever re-registers, {@code CollectorService#register} restores
 * the link from the registration payload.
 */
@Component
public class V6_20260726120000000__Unlink_security_platforms_from_orphaned_collectors
    extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          """
          UPDATE collectors c
          SET collector_security_platform = NULL
          WHERE c.collector_external = true
            AND c.collector_security_platform IS NOT NULL
            AND (c.collector_last_execution IS NULL
                 OR c.collector_last_execution < now() - interval '7 days')
            AND NOT EXISTS (
              SELECT 1 FROM connector_instance_configurations cic
              WHERE cic.connector_instance_configuration_key = 'COLLECTOR_ID'
                AND cic.connector_instance_configuration_value #>> '{}' = c.collector_id
            );
          """);
    }
  }
}
