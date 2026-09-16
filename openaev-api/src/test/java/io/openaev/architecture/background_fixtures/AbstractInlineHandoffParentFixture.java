package io.openaev.architecture.background_fixtures;

import java.util.concurrent.CompletableFuture;

/**
 * Abstract parent whose method body carries the only inline hand-off of its hierarchy ({@code
 * CompletableFuture.supplyAsync}). It is abstract, so {@code isConcreteBean} never enumerates it
 * directly; the entry point is its concrete subclass, which INHERITS this method without
 * redeclaring it and holds no executor field, no annotation and no {@code new Thread} of its own.
 * Mirrors the inheritance shape the annotation and field detectors already handle, for the inline
 * hand-off path.
 */
public abstract class AbstractInlineHandoffParentFixture {

  protected CompletableFuture<String> detach() {
    return CompletableFuture.supplyAsync(this::work);
  }

  private String work() {
    return "done";
  }
}
