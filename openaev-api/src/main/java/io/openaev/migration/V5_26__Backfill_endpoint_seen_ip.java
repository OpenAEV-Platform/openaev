package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Backfills {@code endpoint_seen_ip} for existing endpoints that have IPs but a null seen_ip. This
 * fixes historical data where agentless endpoint creation did not populate seen_ip from the first
 * known IP address.
 */
@Component
public class V5_26__Backfill_endpoint_seen_ip extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "UPDATE assets SET endpoint_seen_ip = endpoint_ips[1] "
              + "WHERE endpoint_seen_ip IS NULL "
              + "AND endpoint_ips IS NOT NULL "
              + "AND array_length(endpoint_ips, 1) > 0");
    }
  }
}
