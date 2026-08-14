package io.openaev.integration;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Creates and initializes per-tenant {@link Manager} instances on behalf of {@link ManagerFactory}.
 *
 * <p>Kept as a separate bean (not a {@code ManagerFactory} method) so every call crosses the Spring
 * proxy and {@code @Transactional} actually applies — a same-class call would silently bypass it
 * (enforced by the {@code no_transactional_self_invocation} architecture rule).
 */
@Service
@RequiredArgsConstructor
public class ManagerCreator {

  private final List<IntegrationFactory> factories;
  private final List<BuiltinTenantRegistrable> builtinRegistrables;
  private final TenantScopedTransaction tenantTx;

  /**
   * Creates a new {@link Manager} for the given tenant, joining the caller's transaction when one
   * exists, or opening its own read-write one otherwise. Deliberately NOT {@code REQUIRES_NEW}: a
   * second DB session's connector-row writes block forever on rows the caller's still-uncommitted
   * transaction already holds (e.g. {@code @Transactional} API tests that insert connectors and
   * then hit a read endpoint) — a self-deadlock PostgreSQL cannot detect because the waiting
   * session's owner is the very thread suspended inside it.
   *
   * <p>Registering the tenant's built-in connectors is a write, so it is skipped when the joined
   * transaction is read-only (e.g. a first-access {@code GET /api/collectors} that runs {@code
   * readOnly} — previously this failed with "cannot execute DELETE in a read-only transaction").
   * Built-ins are guaranteed by the read-write paths instead: {@link
   * ManagerFactory#createDependencyForTenant} on tenant creation, and {@link
   * io.openaev.scheduler.jobs.ManagerIntegrationsSyncJob} which calls {@link
   * ManagerFactory#getManager} inside a read-write tenant transaction on every cycle.
   *
   * <p>Every caller reaches this method through wildly different scoping paths: an HTTP request's
   * own {@code TxCtx}, {@code ManagerIntegrationsSyncJob}'s {@code tenantTx.execute(forTenant(id),
   * ...)}, tenant creation's ambient provisioning transaction, or a caller that sets no scope at
   * all (e.g. {@code ComchecksExecutionJob}'s deliberately transaction-less email-injector lookup,
   * which nonetheless still joins ITS OWN new transaction here via this method's own
   * {@code @Transactional}). Rather than requiring every current and future caller to get its own
   * scoping right, this is the single choke point all of them funnel through: explicitly
   * (re-)assert the scope for {@code tenantId} on whatever transaction we're joining. {@code
   * setScopeOnCurrentTransaction} only sets the GUC on the transaction already open here — it never
   * opens a new one, so it can't break tenant-creation's need to see the still-uncommitted {@code
   * Tenant} row, and it's a safe no-op re-assertion for callers that were already correctly scoped.
   */
  @Transactional
  public Manager createManager(@NotBlank final String tenantId) {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId));
    try {
      if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
        for (BuiltinTenantRegistrable component : builtinRegistrables) {
          component.registerForTenant(tenantId);
        }
      }
      Manager manager = new Manager(tenantId, factories);
      manager.monitorIntegrations();
      return manager;
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize Manager for tenant " + tenantId, e);
    }
  }
}
