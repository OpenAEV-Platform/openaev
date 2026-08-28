package io.openaev.rest;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;

/**
 * Violates the HTTP-path guard: an HTTP-side class reaching for the background primitive.
 * Test-scope only, so the frozen rules on production classes never see it; the fixture test imports
 * it explicitly.
 */
public class PrimitiveOnHttpPathFixture {

  private final TenantScopedTransaction tenantTx;

  public PrimitiveOnHttpPathFixture(TenantScopedTransaction tenantTx) {
    this.tenantTx = tenantTx;
  }

  public long widenSilently() {
    return tenantTx.execute(TxCtx.allTenants(), () -> 0L);
  }
}
