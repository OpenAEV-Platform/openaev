package io.openaev.architecture.background_fixtures;

import org.springframework.boot.CommandLineRunner;

/**
 * Near-miss for the seeding/startup family via the {@code CommandLineRunner} interface (the
 * inherited-{@code @PostConstruct} fixture covers the annotation branch only). A bean implementing
 * {@code CommandLineRunner} runs once at startup, before any request scope exists, so removing the
 * runner predicate would let it escape. Test-scope only; imported explicitly by the detection test.
 */
public class CommandLineRunnerFixture implements CommandLineRunner {

  @Override
  public void run(String... args) {}
}
