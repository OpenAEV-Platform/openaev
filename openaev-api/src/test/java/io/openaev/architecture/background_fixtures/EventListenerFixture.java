package io.openaev.architecture.background_fixtures;

import org.springframework.context.event.EventListener;

/**
 * Near-miss for the event-listener family: a bean whose only background marker is an
 * {@code @EventListener} method. It carries no other marker, so removing the listener predicate
 * would let it escape. Test-scope only; imported explicitly by the detection test.
 */
public class EventListenerFixture {

  @EventListener
  public void onEvent(Object event) {}
}
