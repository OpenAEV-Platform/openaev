package io.openaev.scheduler;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import jakarta.validation.constraints.NotNull;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Runs background work under BOTH tenant scopes of the platform: the v2 primitive (transaction GUC,
 * read by the inspector for activated tables such as collectors) and the v1 thread-local {@link
 * TenantContext}, which {@link io.openaev.aop.HibernateFilterTransactionAspect} turns into the
 * Hibernate {@code tenantFilter} on every {@code @Transactional} method it enters.
 *
 * <p>The v1 bridge is not optional: executing an inject resolves asset groups, endpoints and agents
 * through Criteria queries, all still {@code @Filter} entities. {@link
 * TenantContext#getCurrentTenant()} falls back to the DEFAULT tenant when the thread-local is
 * unset, so without this a customer's simulation resolved the default tenant's endpoints and
 * created its expectations against them - cross-tenant rows, and none for the real targets. It
 * stayed invisible in single-tenant deployments, where that fallback happens to be the right
 * tenant.
 *
 * <p>Callers may run on the shared {@code ForkJoinPool.commonPool} (nested {@code parallelStream}),
 * which also borrows the calling thread: restore the previous value instead of clearing, so the
 * scope of whatever else runs on that thread survives.
 */
@Component
@RequiredArgsConstructor
public class TenantScopedJobRunner {

  private final TenantScopedTransaction tenantTx;

  public void runInTenant(@NotNull final String tenantId, @NotNull final Runnable work) {
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }

  /**
   * Same as {@link #runInTenant} for work that returns a value: opens one tenant-scoped transaction
   * and returns what {@code work} produces. Used where the scoped unit must hand a result back to
   * the caller (a background read that must run under the v2 primitive, e.g. a JOIN FETCH on an
   * active table, or a scoped write returning the persisted row). Kept as its own method (not the
   * body of {@link #runInTenant}) so {@code runInTenant} keeps calling the {@code Runnable}
   * overload of the primitive directly.
   */
  public <T> T supplyInTenant(@NotNull final String tenantId, @NotNull final Supplier<T> work) {
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }

  /**
   * Cross-tenant read for the engine loaders (schedule, notification): opens the primitive under
   * {@link TxCtx#allTenants()}. No v1 {@link TenantContext} bridge here, unlike {@link
   * #supplyInTenant}: {@code allTenants()} has no single tenant to bridge to, and callers that join
   * still-v1 tables disable the v1 filter themselves.
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
   *
   * <p>No v1 {@link TenantContext} bridge: the caller thread's ambient tenant is already whatever
   * it should be, and swapping it here would leak into the caller's remaining work.
   */
  public void runInCurrentTenantTransaction(
      @NotNull final String tenantId, @NotNull final Runnable work) {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId));
    work.run();
  }
}
