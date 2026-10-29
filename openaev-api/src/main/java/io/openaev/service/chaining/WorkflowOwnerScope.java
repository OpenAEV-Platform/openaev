package io.openaev.service.chaining;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TenantScopedTransaction.ScopedWork;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Runs the chaining work of a simulation, a scenario or a deleted injector contract in that owner's
 * own tenant, whatever the scope of the request that reached it.
 *
 * <p>It moves the request's transaction to the owner's one tenant, which may lie outside the
 * request's scope, and checks nothing about the caller. The caller must have checked its access to
 * the owner first, by loading it through the request's access check, and pass that loaded entity.
 * Once access is granted, its workflow follows it: a workflow inherits its owner's tenant and can
 * never move (see {@code Workflow#attributeTenantFromOwner}), so reading it under the caller's
 * scope would miss it whenever that scope does not include the owner's tenant, and a status change,
 * a duplicate or an export would then go on as if the owner were not chained. The classes allowed
 * to call this one are an allowlist pinned by an architecture test, each with the reason its owner
 * was loaded that way.
 *
 * <p>An injector contract is handled the same way: once its deletion is granted, the step templates
 * that reference it are swept in the contract's own tenant, the tenant whose copy of the contract
 * was deleted.
 *
 * <p>The work runs on the request's own transaction (see {@link
 * TenantScopedTransaction#executeOnCurrentTransaction}, including what it flushes when the work
 * throws), so it keeps the transaction's locks, visibility and outcome, and the request's scope is
 * put back after it.
 */
@Component
@RequiredArgsConstructor
public class WorkflowOwnerScope {

  private final TenantScopedTransaction tenantTx;

  /** Runs {@code work} in the tenant of {@code simulation} and returns its result. */
  public <T, E extends Exception> T call(Exercise simulation, ScopedWork<T, E> work) throws E {
    return tenantTx.executeOnCurrentTransaction(scopeOf(simulation.getTenant()), work);
  }

  /** Runs {@code work} in the tenant of {@code scenario} and returns its result. */
  public <T, E extends Exception> T call(Scenario scenario, ScopedWork<T, E> work) throws E {
    return tenantTx.executeOnCurrentTransaction(scopeOf(scenario.getTenant()), work);
  }

  /** Runs {@code work} in the tenant of {@code injectorContract}. */
  public <E extends Exception> void run(InjectorContract injectorContract, ScopedAction<E> work)
      throws E {
    tenantTx.executeOnCurrentTransaction(scopeOf(injectorContract.getTenant()), asWork(work));
  }

  /** Runs {@code work} in the tenant of {@code simulation}. */
  public <E extends Exception> void run(Exercise simulation, ScopedAction<E> work) throws E {
    call(simulation, asWork(work));
  }

  /** Runs {@code work} in the tenant of {@code scenario}. */
  public <E extends Exception> void run(Scenario scenario, ScopedAction<E> work) throws E {
    call(scenario, asWork(work));
  }

  /** Work with no result, allowed to throw a checked exception. */
  @FunctionalInterface
  public interface ScopedAction<E extends Exception> {
    void run() throws E;
  }

  private static <E extends Exception> ScopedWork<Void, E> asWork(ScopedAction<E> work) {
    return () -> {
      work.run();
      return null;
    };
  }

  // The owner's own column, never the caller's context: tenant_id is set at creation and not
  // updatable on exercises and scenarios, and is part of an injector contract's key.
  private static TxCtx scopeOf(Tenant ownerTenant) {
    return TxCtx.forTenant(Objects.requireNonNull(ownerTenant, "owner without a tenant").getId());
  }
}
