package io.openaev.architecture.background_fixtures;

/**
 * Near-miss for the detached hand-off family: work detached through a subclass of {@code Thread}
 * ({@link TenantThreadFixture}) rather than a raw {@code new Thread(...)}, with NO executor field.
 * The constructor owner is the subclass, not {@code java.lang.Thread}, so an exact-owner check
 * misses it; the detector must match constructor owners assignable to {@code java.lang.Thread}.
 * Test-scope only; imported explicitly (with its thread subclass) by the detection test.
 */
public class ThreadSubclassFixture {

  public void detach() {
    TenantThreadFixture thread = new TenantThreadFixture(this::work);
    thread.setDaemon(true);
    thread.start();
  }

  private void work() {}
}
