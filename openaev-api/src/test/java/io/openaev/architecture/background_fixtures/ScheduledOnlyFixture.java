package io.openaev.architecture.background_fixtures;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * Near-miss for the {@code @Scheduled} family: a bean whose ONLY background marker is a
 * {@code @Scheduled} method. It carries no {@code @PostConstruct}, no {@code @Async}, no listener
 * annotation, no executor field and no Quartz interface, so before {@code @Scheduled} was a family
 * of its own it slipped the guard entirely (ImapService was caught only because it also has a
 * {@code @PostConstruct}). Test-scope only: the frozen guard imports production classes with {@code
 * DoNotIncludeTests}, so this fixture never lands in the enumeration; the detection test imports it
 * explicitly.
 */
public class ScheduledOnlyFixture {

  @Scheduled(fixedDelay = 10_000)
  public void poll() {}
}
