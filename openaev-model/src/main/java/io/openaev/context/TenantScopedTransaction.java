package io.openaev.context;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The background-side transaction primitive: opens a transaction AND sets the tenant scope, in one
 * explicit move. Background code must not use {@code @Transactional} (its self-invocation trap
 * silently skips both the transaction and the scope); it opens transactions here instead. HTTP
 * keeps {@code @Transactional} + {@code TxCtx} via the transaction aspect.
 *
 * <p>{@link #execute} refuses to open inside an already-active transaction: a primitive call is an
 * explicit scope boundary, never an implicit join. Deliberate nesting goes through {@link
 * #executeNew}, which opens a new transaction and re-sets the scope there (the {@code
 * set_config(..., true)} is transaction-local, so a nested transaction never inherits it).
 *
 * <p>Self-invocation is not a concern for this class: it is called explicitly, no proxy is
 * involved, so there is no intra-class call that could bypass it.
 *
 * <p>Background-only: HTTP code must not reach for this class (guarded by an ArchUnit rule); the
 * HTTP path carries its scope through {@code @Transactional} + {@code TxCtx} and the aspect.
 *
 * <p>Interaction with the aspect, for per-tenant loops: inside a resolved {@code allTenants()}
 * scope, a joined {@code @Transactional} service method carrying a NARROWER {@code TxCtx} argument
 * trips the aspect's nesting guard (a nested method must not redefine the scope). Narrowing is a
 * scope boundary: open it with {@link #executeNew} and the narrower ctx instead.
 *
 * <p>Poisoning rule: ANY runtime exception thrown by a joined {@code @Transactional} service (the
 * nesting guard, a write refusal, plain business code) marks THIS transaction rollback-only.
 * Catching it and carrying on still dies at commit ({@code UnexpectedRollbackException}), after
 * having worked for nothing. Never catch-and-continue inside the same transaction; catch around an
 * {@link #executeNew} boundary instead, whose {@code REQUIRES_NEW} isolates the failure.
 *
 * <p>Testing note: integration tests around this primitive must NOT be {@code @Transactional} (the
 * {@link #execute} guard refuses an active transaction). Seed and clean through auto-committed JDBC
 * instead; {@code TenantScopedTransactionIntegrationTest} is the reference pattern.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantScopedTransaction {

  private final PlatformTransactionManager transactionManager;
  private final EntityManager entityManager;
  private final TenantScopeIntentionResolver intentionResolver;

  /** Opens a background transaction carrying {@code ctx}; refuses to run inside an active one. */
  public <T> T execute(TxCtx ctx, Supplier<T> work) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "TenantScopedTransaction.execute() refuses to open inside an active transaction: a"
              + " primitive call is an explicit scope boundary. Use executeNew() for deliberate"
              + " nesting.");
    }
    return run(ctx, TransactionDefinition.PROPAGATION_REQUIRED, work);
  }

  /** Same as {@link #execute(TxCtx, Supplier)} for work that returns nothing. */
  public void execute(TxCtx ctx, Runnable work) {
    execute(
        ctx,
        () -> {
          work.run();
          return null;
        });
  }

  /**
   * Opens a NEW transaction (scope boundary) inside an existing one, and re-sets the scope there.
   * Refuses to run outside an active transaction: at the top level, {@link #execute} is the door;
   * this method exists only for deliberate nesting, never as a way around the {@code execute}
   * guard.
   */
  public <T> T executeNew(TxCtx ctx, Supplier<T> work) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "TenantScopedTransaction.executeNew() is for deliberate nesting inside an active"
              + " transaction. At the top level, open through execute().");
    }
    return run(ctx, TransactionDefinition.PROPAGATION_REQUIRES_NEW, work);
  }

  /** Same as {@link #executeNew(TxCtx, Supplier)} for work that returns nothing. */
  public void executeNew(TxCtx ctx, Runnable work) {
    executeNew(
        ctx,
        () -> {
          work.run();
          return null;
        });
  }

  /**
   * Sets the tenant scope on the CURRENT, already-active transaction; opens none. For code that
   * must join an ambient transaction it does not own and needs to see uncommitted writes made
   * earlier in that same transaction — the tenant-onboarding case: {@code TenantService.create()}
   * inserts the new {@code Tenant} row, uncommitted, then dependency managers (including {@code
   * MigrationProcessor}) must write child rows referencing that same tenant id. {@link #executeNew}
   * is the WRONG tool here: {@code REQUIRES_NEW} suspends the ambient transaction and opens a new
   * connection with its own snapshot, which cannot see the uncommitted tenant row — every
   * FK-constrained insert against it fails immediately.
   *
   * <p>Unlike {@link #executeNew}, a failure in the caller's subsequent work marks the WHOLE
   * ambient transaction rollback-only, same as any other code running in it. This is deliberate: a
   * failed provisioning step must not leave a half-provisioned, committed tenant behind — the
   * caller (the create-tenant HTTP transaction) rolls back everything, tenant row included.
   *
   * <p>Refuses to run outside an active transaction: there is no ambient scope to join.
   */
  public void setScopeOnCurrentTransaction(TxCtx ctx) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "TenantScopedTransaction.setScopeOnCurrentTransaction() has nothing to join: no"
              + " transaction is active. It only makes sense inside an ambient transaction opened"
              + " by the caller.");
    }
    setScope(intentionResolver.resolve(ctx).toGuc());
  }

  /**
   * The per-tenant loop idiom: runs {@code work} once for every active tenant, each in its OWN
   * top-level transaction scoped to that tenant. This is the FLAT loop, not a nested one: a tenant
   * that fails is rolled back and cannot poison the others (unlike catching an exception inside a
   * single transaction, which dies at commit). Failures are collected and rethrown as one
   * aggregate, never swallowed. The work receives its tenant id directly; reconstruct the scope
   * with {@code TxCtx.forTenant(id)} if a nested call needs it explicitly (e.g. a joined
   * {@code @Transactional} method taking a {@code TxCtx} argument).
   *
   * <p>This is the intended idiom for the common SEQUENTIAL per-tenant job. The package-private
   * resolver keeps the {@code allTenants()} resolution machinery internal; a job with a legitimate
   * parallel fan-out keeps its own executor and opens one top-level transaction per task with
   * {@code execute(TxCtx.forTenant(id), work)} instead (concurrent scoped transactions are isolated
   * from each other, reads and writes, proven by test).
   *
   * <p>The active-tenant list is read ONCE, at loop start (a snapshot): a tenant created while the
   * loop runs is picked up on the next job fire, not mid-loop. This differs on purpose from {@code
   * execute(allTenants(), …)}, which re-resolves the intention at every transaction. A per-tenant
   * loop wants a stable domain; a single global-scope transaction wants freshness.
   *
   * <p>Scale note: the loop is serial and single-threaded, one transaction per tenant. It is meant
   * for the current tenant counts; batching, parallelism or a per-tenant timeout are out of scope
   * here. On partial failure it runs every tenant and throws at the end, so the caller (a job) is
   * marked failed even when most tenants succeeded: read the per-tenant {@code log.warn} and the
   * aggregate's suppressed causes to see what actually happened.
   */
  public void forEachTenant(Consumer<String> work) {
    // Checked upfront: a sequence of top-level transactions cannot run inside an active one. The
    // per-tenant execute() would refuse each tenant anyway, but N aggregated refusals would bury
    // the actual reason.
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "TenantScopedTransaction.forEachTenant() refuses to run inside an active transaction:"
              + " each tenant opens its own top-level transaction. Use executeNew() for deliberate"
              + " nesting.");
    }
    List<String> tenantIds = intentionResolver.activeTenantIds();
    Map<String, RuntimeException> failures = new LinkedHashMap<>();
    for (String tenantId : tenantIds) {
      try {
        TxCtx scope = TxCtx.forTenant(tenantId);
        execute(scope, () -> work.accept(tenantId));
      } catch (RuntimeException failure) {
        failures.put(tenantId, failure);
        log.warn("forEachTenant: tenant {} failed, continuing with the others", tenantId, failure);
      }
    }
    if (!failures.isEmpty()) {
      IllegalStateException aggregate =
          new IllegalStateException(
              "forEachTenant: "
                  + failures.size()
                  + " of "
                  + tenantIds.size()
                  + " tenant(s) failed");
      failures.values().forEach(aggregate::addSuppressed);
      throw aggregate;
    }
  }

  private <T> T run(TxCtx ctx, int propagation, Supplier<T> work) {
    requireScope(ctx);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(propagation);
    return transaction.execute(
        status -> {
          // Resolved inside the transaction: an intention is re-resolved at every short
          // transaction, so a long-running job naturally sees tenants created in between.
          setScope(intentionResolver.resolve(ctx).toGuc());
          return work.get();
        });
  }

  private static void requireScope(TxCtx ctx) {
    Objects.requireNonNull(ctx, "a background transaction needs a tenant scope");
    if (ctx instanceof TxCtx.Missing) {
      throw new IllegalArgumentException(
          "a background transaction cannot open with Missing: it would be fail-closed by"
              + " construction and read nothing. Carry a real tenant scope.");
    }
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }
}
