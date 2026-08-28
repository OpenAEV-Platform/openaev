package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Forces the new built-in default home dashboard for every user after the home redesign (issue
 * #6753): resets BOTH override levels of the home resolution chain (user profile preference, then
 * tenant setting) so everyone lands on the platform default command center after the release. Users
 * and admins can re-select a custom dashboard afterwards in Profile / Settings - custom dashboards
 * themselves are untouched.
 *
 * <p>The starter pack cannot re-seed the tenant setting: it only runs once, guarded by the
 * platform-level {@code starterpack} setting key which is left in place.
 *
 * <p>Idempotent and lock-light (targeted UPDATE / DELETE on small tables).
 */
@Component
public class V6_20260717221000000__Force_default_home_dashboard extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // -- 1. Clear the per-user Profile override --
      statement.execute(
          "UPDATE users SET user_home_dashboard = NULL WHERE user_home_dashboard IS NOT NULL;");

      // -- 2. Clear the tenant-level Settings override AND the legacy platform-level parameter.
      // Tenant settings live in the parameters table since V5_03 merged (and dropped) the
      // transient tenant_settings table: tenant-scoped rows carry a NON NULL tenant_id; legacy
      // pre-tenancy installs may still have a platform-level row (tenant_id IS NULL). DELETE both
      // (parameter_value is NOT NULL since the original schema, so setting it to NULL is not an
      // option): every reader goes through Optional and falls through to the built-in default. --
      statement.execute("DELETE FROM parameters WHERE parameter_key = 'platform_home_dashboard';");
    }
  }
}
