package io.openaev.config;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
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

  /**
   * Arms production's activation list in every context, over a list the test class sets itself.
   * Half the HTTP test classes arm only the table they test, so without it an association into
   * another active table reads ungated and the probe cannot see it.
   */
  static final String PRODUCTION_TABLES_PROPERTY = "openaev.lazyprobe.production-tables";

  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
    return "on".equals(System.getProperty(ENABLED_PROPERTY))
        ? new ProbeCustomizer("on".equals(System.getProperty(PRODUCTION_TABLES_PROPERTY)))
        : null;
  }

  /** Adds the probe's post-processor to a test context. */
  static final class ProbeCustomizer implements ContextCustomizer {

    private final boolean productionTables;

    ProbeCustomizer(boolean productionTables) {
      this.productionTables = productionTables;
    }

    @Override
    public void customizeContext(
        ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
      if (productionTables) {
        context
            .getEnvironment()
            .getPropertySources()
            .addFirst(
                new MapPropertySource(
                    "lazyAfterCommitProbeProductionTables",
                    Map.of(
                        "openaev.tenant.active-tables",
                        String.join(",", ProductionActiveTables.names()))));
      }
      context
          .getBeanFactory()
          .addBeanPostProcessor(
              LazyAfterCommitProbeTestConfig.probeWiring(context.getEnvironment()));
      context
          .getBeanFactory()
          .registerSingleton(
              "lazyAfterCommitProbeRequestIsolation",
              new LazyProbeRequestIsolation(() -> context.getBean(EntityManager.class)));
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof ProbeCustomizer customizer
          && customizer.productionTables == productionTables;
    }

    @Override
    public int hashCode() {
      return Boolean.hashCode(productionTables);
    }
  }
}
