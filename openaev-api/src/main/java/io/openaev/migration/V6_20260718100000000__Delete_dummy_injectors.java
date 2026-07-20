package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Deletes the placeholder "dummy" injectors ({@code injector_type} suffixed with {@code _dummy})
 * that the starter-pack importer used to create before the real injectors registered. The mechanism
 * has been removed from the codebase: starter-pack contracts are now imported without an injector
 * link and adopted by the real injector on registration.
 *
 * <p>Order matters: injects created from a dummy-linked contract carry {@code inject_injector}
 * pointing at the dummy row, and that FK is ON DELETE CASCADE - the reference must be detached
 * first, otherwise deleting the dummy would silently delete the imported injects. The contracts
 * themselves are left in place (now injector-less) so the real injector merges and adopts them by
 * id when it registers; join-table rows cascade with the injector delete.
 *
 * <p>Idempotent and lock-light (targeted UPDATE / DELETE on small result sets).
 *
 * <p>PERFORMANCE (issue #6780): {@code injects.inject_injector} carries a composite FK {@code
 * (inject_injector, tenant_id) ON DELETE CASCADE} (V4_78, remade composite in V5_07) but never had
 * a supporting index. PostgreSQL runs the cascade check once per deleted parent row, so without an
 * index every dummy injector deleted below triggers a full sequential scan of the {@code injects}
 * table. On a large multi-tenant platform this ran for longer than the startup probe window,
 * leaving an orphaned server-side session holding the Flyway advisory lock and crash-looping every
 * subsequent boot. The index is therefore created first, turning both the detach UPDATE and the
 * cascade checks into index lookups; it is also permanently useful for runtime injector deletions.
 */
@Component
public class V6_20260718100000000__Delete_dummy_injectors extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // -- 0. Ensure the FK-supporting index exists (see class javadoc). Partial: NULL rows are
      // never looked up by the FK cascade and the detach below nulls the column, keeping it small.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_injector "
              + "ON injects (inject_injector, tenant_id) "
              + "WHERE inject_injector IS NOT NULL;");

      // -- 1. Detach injects from dummy injectors (FK is ON DELETE CASCADE) --
      statement.execute(
          "UPDATE injects SET inject_injector = NULL "
              + "FROM injectors i "
              + "WHERE injects.inject_injector = i.injector_id "
              + "AND injects.tenant_id = i.tenant_id "
              + "AND i.injector_type LIKE '%\\_dummy' ESCAPE '\\';");

      // -- 2. Delete the dummy injectors (injectors_injector_contracts rows cascade) --
      statement.execute("DELETE FROM injectors WHERE injector_type LIKE '%\\_dummy' ESCAPE '\\';");
    }
  }
}
