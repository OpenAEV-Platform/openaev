package io.openaev.config;

import java.util.List;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Suite-wide activation of the WS1 fail-closed detector, gated by {@code
 * -Dopenaev.failclosed.detector=on}. When on, every {@code @SpringBootTest} context wraps its
 * DataSource with {@link FailClosedDetectorListener}, so a full integration run surfaces every
 * unscoped read of an active table (the baseline, then the gate). Off by default, so normal CI is
 * untouched and only an opt-in run pays the cost.
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
      Environment environment = context.getEnvironment();
      FailClosedDetectorListener listener = new FailClosedDetectorListener(environment);
      context
          .getBeanFactory()
          .addBeanPostProcessor(
              new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                  if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
                    return ProxyDataSourceBuilder.create(dataSource)
                        .name("failclosed-detector")
                        .listener(listener)
                        .build();
                  }
                  return bean;
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
