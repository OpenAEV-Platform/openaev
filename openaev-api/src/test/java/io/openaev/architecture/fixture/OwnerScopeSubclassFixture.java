package io.openaev.architecture.fixture;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.Scenario;
import io.openaev.service.chaining.WorkflowOwnerScope;

/**
 * Violates the caller allowlist through inheritance: calls a method WorkflowOwnerScope declares.
 */
public class OwnerScopeSubclassFixture extends WorkflowOwnerScope {

  public OwnerScopeSubclassFixture(TenantScopedTransaction tenantTx) {
    super(tenantTx);
  }

  public static void runInOwnerTenant(OwnerScopeSubclassFixture scope, Scenario scenario) {
    scope.run(scenario, () -> {});
  }
}
