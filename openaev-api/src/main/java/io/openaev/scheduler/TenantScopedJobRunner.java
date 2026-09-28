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
}
