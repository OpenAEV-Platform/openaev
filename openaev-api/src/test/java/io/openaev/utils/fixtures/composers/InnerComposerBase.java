package io.openaev.utils.fixtures.composers;

public abstract class InnerComposerBase<T> {
  public abstract InnerComposerBase<T> persist();

  public abstract InnerComposerBase<T> delete();

  public abstract T get();
}
