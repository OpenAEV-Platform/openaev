package io.openaev.integration;

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
   */
  @Transactional
  public Manager createManager(@NotBlank final String tenantId) {
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
