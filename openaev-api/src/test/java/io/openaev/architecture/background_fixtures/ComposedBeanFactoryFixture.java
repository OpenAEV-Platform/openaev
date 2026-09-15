package io.openaev.architecture.background_fixtures;

import org.springframework.boot.CommandLineRunner;

/**
 * Near-miss for the {@code @Bean}-factory recognition through a composed annotation: this
 * configuration class carries no background marker of its own and its factory method is annotated
 * with {@link ComposedBean} (meta-annotated {@code @Bean}), not a direct {@code @Bean}. The
 * returned {@code CommandLineRunner} lambda is anonymous and excluded from the scan, so a
 * direct-only {@code @Bean} check would let the declaring class slip both the family scan and the
 * baseline. Test-scope only; imported explicitly by the detection test.
 */
public class ComposedBeanFactoryFixture {

  @ComposedBean
  public CommandLineRunner seed() {
    return args -> {};
  }
}
