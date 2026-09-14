package io.openaev.architecture.background_fixtures;

/**
 * Near-miss for the {@code @Scheduled} family through a composed annotation: the only marker is
 * {@link ComposedScheduled}, which is meta-annotated {@code @Scheduled}. A direct-only detector
 * sees no {@code @Scheduled} and lets this poller escape; the meta-annotation-aware predicate must
 * catch it. Test-scope only; imported explicitly by the detection test.
 */
public class ComposedScheduledFixture {

  @ComposedScheduled
  public void poll() {}
}
