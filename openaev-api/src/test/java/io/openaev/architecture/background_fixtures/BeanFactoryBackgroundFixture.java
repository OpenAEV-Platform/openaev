package io.openaev.architecture.background_fixtures;

import org.quartz.Job;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Near-miss for the {@code @Bean}-factory recognition of the type-based families. This
 * configuration class carries no background marker of its own; its {@code @Bean} methods return
 * lambda instances of {@code CommandLineRunner} / {@code ApplicationRunner} (seeding), {@code
 * ApplicationListener} (listener) and {@code org.quartz.Job} (quartz). The lambdas are anonymous
 * and excluded from the scan by {@code isConcreteBean}, so without reading the factory return types
 * the declaring class would slip both the family scan and the baseline. Test-scope only; imported
 * explicitly by the detection test.
 */
public class BeanFactoryBackgroundFixture {

  @Bean
  public CommandLineRunner seedCommandLine() {
    return args -> {};
  }

  @Bean
  public ApplicationRunner seedApplication() {
    return args -> {};
  }

  @Bean
  public ApplicationListener<ContextRefreshedEvent> refreshListener() {
    return event -> {};
  }

  @Bean
  public Job quartzJob() {
    return context -> {};
  }
}
