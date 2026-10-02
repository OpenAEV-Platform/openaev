package io.openaev.architecture.background_fixtures;

import java.util.concurrent.Executor;

/**
 * Near-miss for the {@code getFields()} blind spot: a concrete bean whose only detached hand-off
 * marker, an {@code Executor} field, is INHERITED from {@link AbstractExecutorFieldParentFixture}
 * and not redeclared here. It has no inline hand-off, no {@code CompletableFuture} and no {@code
 * new Thread}, so the only signal is the inherited field. {@code JavaClass.getFields()} returns
 * declared fields only, so it misses it; {@code getAllFields()} sees it. Test-scope only; imported
 * explicitly by the detection test.
 */
public class InheritedExecutorFieldFixture extends AbstractExecutorFieldParentFixture {

  public InheritedExecutorFieldFixture(Executor pool) {
    super(pool);
  }
}
