package io.openaev.architecture.fixture;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import java.util.function.Consumer;

/**
 * Violates the scope-setter baseline: sets the current transaction's scope without being listed.
 */
public class ScopeSetterOutsideBaselineFixture {

  private final TenantScopedTransaction tenantTx;

  public ScopeSetterOutsideBaselineFixture(TenantScopedTransaction tenantTx) {
    this.tenantTx = tenantTx;
  }

  public void moveTo(String tenantId) {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId));
  }

  public Consumer<TxCtx> asReference() {
    return tenantTx::setScopeOnCurrentTransaction;
  }
}
