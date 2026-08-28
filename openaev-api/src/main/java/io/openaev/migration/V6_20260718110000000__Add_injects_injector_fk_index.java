package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Ensures the FK-supporting index on {@code injects.inject_injector} exists everywhere (issue
 * #6780). The composite FK {@code (inject_injector, tenant_id) ON DELETE CASCADE} introduced in
 * V4_78 / V5_07 never had a supporting index, so every {@code injectors} row deletion triggered a
 * full sequential scan of {@code injects} per deleted row - which made the dummy injector cleanup
 * (V6_20260718100000000) outlast the startup probe and crash-loop the platform.
 *
 * <p>The index is now created inside the (fixed) dummy cleanup migration itself; this follow-up
 * covers platforms that had already applied it before the fix. {@code IF NOT EXISTS} makes it a
 * no-op everywhere else.
 *
 * <p>Partial index: NULL values are never looked up by FK cascade checks, keeping the index small.
 */
@Component
public class V6_20260718110000000__Add_injects_injector_fk_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_injector "
              + "ON injects (inject_injector, tenant_id) "
              + "WHERE inject_injector IS NOT NULL;");
    }
  }
}
