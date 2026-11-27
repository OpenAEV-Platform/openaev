package io.openaev.config;

import java.util.List;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Suite-wide activation of the WS1 fail-closed detector, gated by {@code
 * -Dopenaev.failclosed.detector=on}. When on, every {@code @SpringBootTest} context wraps its
 * DataSource with {@link FailClosedDetectorListener}, so a full integration run surfaces every
 * unscoped read of an active table (the baseline, then the gate). Off by default; every API shard
 * of the pull request CI turns it on (.github/actions/api-tests/action.yml).
 *
 * <p>Registered as a {@code ContextCustomizerFactory} in test {@code META-INF/spring.factories}.
 * The customizer is a single shared instance comparing equal by type, so it does not fragment the
 * context cache across test classes.
 */
public class FailClosedDetectorContextCustomizerFactory implements ContextCustomizerFactory {

  private static final ContextCustomizer CUSTOMIZER = new DetectorCustomizer();

  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
    return "on".equals(System.getProperty("openaev.failclosed.detector")) ? CUSTOMIZER : null;
  }

  /** Adds the detector as a DataSource-wrapping bean post-processor to a test context. */
  static final class DetectorCustomizer implements ContextCustomizer {

    @Override
    public void customizeContext(
        ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
      FailClosedDetectorListener listener = new FailClosedDetectorListener();
      context
          .getBeanFactory()
          .addBeanPostProcessor(
              new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                  return DetectorDataSourceProxies.wrapOrChain(
                      bean, "failclosed-detector", listener);
                }
              });
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof DetectorCustomizer;
    }

    @Override
    public int hashCode() {
      return DetectorCustomizer.class.hashCode();
    }
  }
}
