package io.openaev.scheduler;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import jakarta.validation.constraints.NotNull;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Runs background work inside a transaction scoped to one tenant, the v2 primitive: the statement
 * inspector reads its scope, and every write of that work names its tenant from the row it belongs
 * to.
 */
@Component
@RequiredArgsConstructor
public class TenantScopedJobRunner {

  private final TenantScopedTransaction tenantTx;

  public void runInTenant(@NotNull final String tenantId, @NotNull final Runnable work) {
    tenantTx.execute(TxCtx.forTenant(tenantId), work);
  }

  /**
   * Same as {@link #runInTenant} for work that returns a value: opens one tenant-scoped transaction
   * and returns what {@code work} produces, for a scoped unit that hands a result back to the
   * caller (a background read under the v2 primitive, or a scoped write returning the persisted
   * row).
   */
  public <T> T supplyInTenant(@NotNull final String tenantId, @NotNull final Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(tenantId), work);
  }

  /**
   * Cross-tenant read for the engine loaders (schedule, notification): opens the primitive under
   * {@link TxCtx#allTenants()}.
   */
  public <T> T supplyAcrossTenants(@NotNull final Supplier<T> work) {
    return tenantTx.execute(TxCtx.allTenants(), work);
  }

  /**
   * Scopes the caller's ALREADY-OPEN transaction to {@code tenantId} and runs {@code work} inside
   * it. Opens nothing: for a synchronous write that belongs to the caller's unit of work (a
   * fail-fast status update on a row the caller created earlier in that same transaction, still
   * uncommitted).
   *
   * <p>Nesting a {@code REQUIRES_NEW} transaction there is wrong twice over. It reads its own
   * snapshot and cannot see the caller's uncommitted row, so a {@code save} resolves to a merge
   * whose select finds nothing and Hibernate rejects the entity outright ({@code
   * StaleObjectStateException}), failing the caller's whole unit of work. And a status written in a
   * separate transaction would outlive a caller rollback that also removes the row it describes.
   */
  public void runInCurrentTenantTransaction(
      @NotNull final String tenantId, @NotNull final Runnable work) {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId));
    work.run();
  }
}
