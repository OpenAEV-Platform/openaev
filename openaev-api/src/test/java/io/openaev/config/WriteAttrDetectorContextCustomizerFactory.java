package io.openaev.config;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Suite-wide activation of the write-attribution detector, gated by {@code
 * -Dopenaev.writeattr.detector=on}. When on, every {@code @SpringBootTest} context wraps its
 * DataSource with {@link WriteAttrDetectorListener} and installs {@link WriteAttrDetectorTrigger}
 * on refresh, so a full integration run surfaces every write that stamps a {@code tenant_id}
 * outside the transaction's v2 scope. Off by default, so normal CI is untouched. Mirrors {@link
 * FailClosedDetectorContextCustomizerFactory}; used to collect the baseline and, once frozen, to
 * feed the nightly shadow report.
 */
public class WriteAttrDetectorContextCustomizerFactory implements ContextCustomizerFactory {

  private static final ContextCustomizer CUSTOMIZER = new DetectorCustomizer();
  // The test database is shared across contexts in one JVM, so the trigger is installed once.
  private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
    return "on".equals(System.getProperty("openaev.writeattr.detector")) ? CUSTOMIZER : null;
  }

  /**
   * Wraps the DataSource with the detector listener and installs the trigger on context refresh.
   */
  static final class DetectorCustomizer implements ContextCustomizer {

    @Override
    public void customizeContext(
        ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
      WriteAttrDetectorListener listener = new WriteAttrDetectorListener();
      context
          .getBeanFactory()
          .addBeanPostProcessor(
              new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                  if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
                    return ProxyDataSourceBuilder.create(dataSource)
                        .name("writeattr-detector")
                        .listener(listener)
                        .build();
                  }
                  return bean;
                }
              });
      context.addApplicationListener(
          event -> {
            if (event instanceof org.springframework.context.event.ContextRefreshedEvent
                && INSTALLED.compareAndSet(false, true)) {
              installTrigger(context);
            }
          });
    }

    private void installTrigger(ConfigurableApplicationContext context) {
      DataSource dataSource = context.getBean(DataSource.class);
      try (Connection connection = dataSource.getConnection()) {
        WriteAttrDetectorTrigger.install(connection);
        if (!connection.getAutoCommit()) {
          connection.commit();
        }
      } catch (Exception e) {
        throw new IllegalStateException("cannot install the write-attribution trigger", e);
      }
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
