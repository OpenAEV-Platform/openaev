package io.openaev.architecture.fixture;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;

/** Violates the move rule: moves the current transaction to a tenant without WorkflowOwnerScope. */
public class CurrentTransactionMoveFixture {

  private final TenantScopedTransaction tenantTx;

  public CurrentTransactionMoveFixture(TenantScopedTransaction tenantTx) {
    this.tenantTx = tenantTx;
  }

  public long readInTenant(String tenantId) {
    return tenantTx.executeOnCurrentTransaction(TxCtx.forTenant(tenantId), () -> 0L);
  }
}
