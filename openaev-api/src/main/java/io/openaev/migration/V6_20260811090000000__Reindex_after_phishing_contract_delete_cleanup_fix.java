package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the inject, inject-expectation and finding indexes after closing the phishing
 * cascade-delete indexing gap.
 *
 * <p>{@code V6_20260725100000000} converged the engine after the contract-popover cascade-delete
 * fix, but the phishing landing page deletion path had the same gap: {@code
 * PhishingLandingPageService.deleteInjectorContract} removed the landing page's synthesized
 * contract without collecting the injects that {@code injects.inject_injector_contract ON DELETE
 * CASCADE} takes down with it. The engine cascade is single-hop, so even the contract's own delete
 * event could never reach the expectation and finding documents - they depend on the INJECT id -
 * and those documents stayed in the indexes, keeping deleted phishing injects visible in every
 * ES-backed statistic (home dashboard tiles, coverage, KPIs).
 *
 * <p>The write path is fixed in code; this migration purges what accumulated since the last
 * convergence. Dropping the {@code indexing_status} row of a type makes the engine driver drop,
 * recreate and fully re-feed that index from PostgreSQL at the next startup, so only the three
 * affected types are rebuilt instead of every index. Idempotent and lock-light (targeted DELETE on
 * a tiny bookkeeping table).
 */
@Component
public class V6_20260811090000000__Reindex_after_phishing_contract_delete_cleanup_fix
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
