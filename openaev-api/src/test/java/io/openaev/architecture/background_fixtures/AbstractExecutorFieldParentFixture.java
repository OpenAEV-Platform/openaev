package io.openaev.architecture.background_fixtures;

import java.util.concurrent.Executor;

/**
 * Abstract parent carrying the only executor field of its hierarchy. It is abstract, so the guard's
 * {@code isConcreteBean} filter never enumerates it directly; the entry point is its concrete
 * subclass, which INHERITS this field without redeclaring it. Mirrors the field-typed detached
 * hand-off family, where the pool is declared once on a shared base.
 */
public abstract class AbstractExecutorFieldParentFixture {

  private final Executor pool;

  protected AbstractExecutorFieldParentFixture(Executor pool) {
    this.pool = pool;
  }

  protected Executor pool() {
    return pool;
  }
}
