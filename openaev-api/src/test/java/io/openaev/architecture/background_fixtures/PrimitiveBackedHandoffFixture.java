package io.openaev.architecture.background_fixtures;

import io.openaev.context.TenantScopedTransaction;

/**
 * A concrete background entry point (an inline {@code new Thread} hand-off) that now references the
 * primitive {@link TenantScopedTransaction} through a direct dependency. It stands in for a waived
 * class that has since been scoped: the stale-entry check must report its baseline waiver as
 * obsolete ({@code isOnPrimitive} branch). Test-scope only; imported explicitly by the detection
 * test.
 */
public class PrimitiveBackedHandoffFixture {

  private final TenantScopedTransaction primitive;

  public PrimitiveBackedHandoffFixture(TenantScopedTransaction primitive) {
    this.primitive = primitive;
  }

  public void detach() {
    Thread thread = new Thread(this::work, "fixture-thread");
    thread.setDaemon(true);
    thread.start();
  }

  private void work() {
    primitive.toString();
  }
}
