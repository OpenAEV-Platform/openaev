package io.openaev.architecture.background_fixtures;

/**
 * A {@code Thread} subclass, standing in for a bespoke thread type a developer might introduce
 * (e.g. one that carries a captured tenant). It exists only so {@link ThreadSubclassFixture} can
 * detach work through {@code new TenantThreadFixture(...)} rather than a raw {@code new
 * Thread(...)}. Test-scope only.
 */
public class TenantThreadFixture extends Thread {

  public TenantThreadFixture(Runnable target) {
    super(target);
  }
}
