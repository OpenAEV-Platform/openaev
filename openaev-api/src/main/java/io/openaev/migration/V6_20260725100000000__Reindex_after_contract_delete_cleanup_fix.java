package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the inject, inject-expectation and finding indexes after closing the last cascade-delete
 * indexing gap.
 *
 * <p>{@code V6_20260723120000000} converged the engine after the cascade-delete fixes, but one
 * write path was still missing its compensation: deleting a custom contract through {@code DELETE
 * /api/injector_contracts/{id}} (the contract popover of an injector in Integrations) removed the
 * entity without collecting the injects that {@code injects.inject_injector_contract ON DELETE
 * CASCADE} takes down with it. The engine cascade is single-hop, so even the contract's own delete
 * event could never reach the expectation and finding documents - they depend on the INJECT id -
 * and those documents stayed in the indexes, keeping deleted atomic testings visible in every
 * ES-backed statistic (home dashboard tiles, coverage, KPIs).
 *
 * <p>The write path is fixed in code; this migration purges what accumulated since the last
 * convergence. Dropping the {@code indexing_status} row of a type makes the engine driver drop,
 * recreate and fully re-feed that index from PostgreSQL at the next startup, so only the three
 * affected types are rebuilt instead of every index. Endpoint documents are left alone on purpose:
 * their denormalized finding references are already refreshed by the {@code
 * update_asset_updated_at_after_delete_finding} trigger. Idempotent and lock-light (targeted DELETE
 * on a tiny bookkeeping table).
 */
@Component
public class V6_20260725100000000__Reindex_after_contract_delete_cleanup_fix
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "DELETE FROM indexing_status "
              + "WHERE indexing_status_type IN ('inject', 'expectation-inject', 'finding');");
    }
  }
}
