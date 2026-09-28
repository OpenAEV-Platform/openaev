package io.openaev.architecture.background_fixtures;

import jakarta.annotation.PostConstruct;

/**
 * Abstract parent carrying the background marker ({@code @PostConstruct}). It is abstract, so the
 * guard's {@code isConcreteBean} filter never enumerates it directly; the entry point is its
 * concrete subclass, which INHERITS this method without redeclaring it. Mirrors the production
 * shape of {@code SelfConfiguredPlatformJob} / {@code EngineSyncExecutionJob}.
 */
public abstract class AbstractPostConstructParentFixture {

  @PostConstruct
  public void register() {}
}
