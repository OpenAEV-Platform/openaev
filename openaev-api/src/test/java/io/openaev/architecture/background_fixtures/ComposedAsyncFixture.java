package io.openaev.architecture.background_fixtures;

/**
 * Near-miss for the {@code @Async} family through a composed annotation: the only marker is {@link
 * ComposedAsync}, which is meta-annotated {@code @Async}. A direct-only detector sees no
 * {@code @Async} and lets it escape; the meta-annotation-aware predicate must catch it. Test-scope
 * only; imported explicitly by the detection test.
 */
public class ComposedAsyncFixture {

  @ComposedAsync
  public void runAsync() {}
}
