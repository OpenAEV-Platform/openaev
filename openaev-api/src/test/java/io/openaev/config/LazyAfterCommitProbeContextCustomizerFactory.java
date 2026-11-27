package io.openaev.config;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Run-wide activation of the lazy-after-commit probe, gated by {@code -Dopenaev.lazyprobe=on}. Off
 * by default: the probe replays every read it flags, which only a measurement run should pay for.
 *
 * <p>Each flagged read is printed with a {@code [LAZYAFTERCOMMIT]} marker and, when {@code
 * -Dopenaev.lazyprobe.out=<file>} is set, appended to that file as one tab-separated line, so a run
 * over many classes can be aggregated per endpoint.
 */
public class LazyAfterCommitProbeContextCustomizerFactory implements ContextCustomizerFactory {

  static final String ENABLED_PROPERTY = "openaev.lazyprobe";

  private static final ContextCustomizer CUSTOMIZER = new ProbeCustomizer();

  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
    return "on".equals(System.getProperty(ENABLED_PROPERTY)) ? CUSTOMIZER : null;
  }

  /**
   * Adds the probe's post-processor to a test context. A single shared instance comparing equal by
   * type, so it does not fragment the context cache across test classes.
   */
  static final class ProbeCustomizer implements ContextCustomizer {

    @Override
    public void customizeContext(
        ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
      context.getBeanFactory().addBeanPostProcessor(LazyAfterCommitProbeTestConfig.probeWiring());
      context
          .getBeanFactory()
          .registerSingleton(
              "lazyAfterCommitProbeRequestIsolation",
              new LazyProbeRequestIsolation(() -> context.getBean(EntityManager.class)));
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof ProbeCustomizer;
    }

    @Override
    public int hashCode() {
      return ProbeCustomizer.class.hashCode();
    }
  }
}
