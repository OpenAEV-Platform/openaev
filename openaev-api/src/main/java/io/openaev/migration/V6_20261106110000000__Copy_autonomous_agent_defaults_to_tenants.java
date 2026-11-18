package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * The default additional agents of the autonomous run ({@code
 * platform_autonomous_additional_agents} and {@code platform_autonomous_additional_agent_modes})
 * are tenant configuration, read and written under the caller's own tenant. This migration copies
 * each platform row ({@code tenant_id IS NULL}) of those two keys to every live tenant (one with no
 * {@code tenant_deleted_at}) that has no row for that key, then deletes the platform row, so an
 * existing deployment keeps the value it had. Re-running it finds no platform row and changes
 * nothing.
 *
 * <p>It copies to the live tenants that exist when it runs. A tenant created afterwards gets no
 * copy: tenant creation ({@code TenantService#create} and the dependency managers it runs) writes
 * none of these keys, they have no platform fallback, and the platform row is gone, so such a
 * tenant starts with the built-in default until an administrator sets its own.
 */
@Component
public class V6_20261106110000000__Copy_autonomous_agent_defaults_to_tenants
    extends BaseJavaMigration {

  private static final String KEYS =
      "'platform_autonomous_additional_agents', 'platform_autonomous_additional_agent_modes'";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          INSERT INTO parameters (parameter_id, parameter_key, parameter_value, tenant_id)
          SELECT gen_random_uuid()::text, platform.parameter_key, platform.parameter_value,
                 t.tenant_id
          FROM parameters platform
          CROSS JOIN tenants t
          WHERE platform.tenant_id IS NULL
            AND t.tenant_deleted_at IS NULL
            AND platform.parameter_key IN (%s)
            AND NOT EXISTS (
              SELECT 1 FROM parameters own
              WHERE own.parameter_key = platform.parameter_key
                AND own.tenant_id = t.tenant_id)
          """
              .formatted(KEYS));
      statement.execute(
          "DELETE FROM parameters WHERE tenant_id IS NULL AND parameter_key IN (%s)"
              .formatted(KEYS));
    }
  }
}
