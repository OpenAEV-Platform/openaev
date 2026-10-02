package io.openaev.architecture.background_fixtures;

/**
 * Near-miss for the event-listener family through a composed annotation: the only marker is {@link
 * ComposedEventListener}, which is meta-annotated {@code @EventListener}. A direct-only detector
 * sees no {@code @EventListener} and lets it escape; the meta-annotation-aware predicate must catch
 * it. Test-scope only; imported explicitly by the detection test.
 */
public class ComposedEventListenerFixture {

  @ComposedEventListener
  public void onEvent(Object event) {}
}
