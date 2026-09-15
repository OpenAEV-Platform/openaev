package io.openaev.config;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Suite-wide activation of the write-attribution detector, gated by {@code
 * -Dopenaev.writeattr.detector=on}. When on, every {@code @SpringBootTest} context wraps its
 * DataSource with {@link WriteAttrDetectorListener} and installs {@link WriteAttrDetectorTrigger}
 * on refresh, so a full integration run surfaces every write that stamps a {@code tenant_id}
 * outside the transaction's v2 scope (or a null one on a strict table). Off by default, so normal
 * CI is untouched. Mirrors {@link FailClosedDetectorContextCustomizerFactory}; used to collect the
 * baseline and, once frozen, to feed the nightly shadow report.
 *
 * <p>The trigger is installed once per JVM on the first context refresh and dropped on a JVM
 * shutdown hook. The test database is shared across contexts in one JVM, so a per-context install
 * would churn; the install is idempotent and versioned, so a crashed run that never reached the
 * shutdown hook cannot leave a differently shaped trigger for the next run (see {@link
 * WriteAttrDetectorTrigger#install}).
 */
public class WriteAttrDetectorContextCustomizerFactory implements ContextCustomizerFactory {

  private static final ContextCustomizer CUSTOMIZER = new DetectorCustomizer();
  private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
  private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

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
      WriteAttrDetectorListener listener =
          new WriteAttrDetectorListener(
              WriteAttrDetectorListener.classifierFrom(
                  () -> context.getBean(EntityManagerFactory.class),
                  () -> context.getBean(DataSource.class)));
      context
          .getBeanFactory()
          .addBeanPostProcessor(
              new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                  return DetectorDataSourceProxies.wrapOrChain(
                      bean, "writeattr-detector", listener);
                }
              });
      context.addApplicationListener(
          event -> {
            if (!(event instanceof ContextRefreshedEvent)) {
              return;
            }
            // A slice context may have no JPA and no DataSource (e.g. a cache-manager unit slice);
            // it can issue no watched write, so skip both wirings rather than fail its refresh.
            EntityManagerFactory entityManagerFactory =
                context.getBeanProvider(EntityManagerFactory.class).getIfAvailable();
            if (entityManagerFactory != null) {
              // Attribution listeners are per SessionFactory, so wire them on every context refresh
              // (the registration is idempotent per factory).
              WriteAttrHibernateListeners.register(entityManagerFactory);
            }
            DataSource dataSource = context.getBeanProvider(DataSource.class).getIfAvailable();
            // The trigger install is JVM-once because the test database is shared across contexts.
            if (dataSource != null && INSTALLED.compareAndSet(false, true)) {
              installTrigger(dataSource);
            }
          });
    }

    private void installTrigger(DataSource dataSource) {
      try (Connection connection = dataSource.getConnection()) {
        WriteAttrDetectorTrigger.install(connection, TenantTables.selfIsolatedTables());
        if (!connection.getAutoCommit()) {
          connection.commit();
        }
      } catch (Exception e) {
        throw new IllegalStateException("cannot install the write-attribution trigger", e);
      }
      registerShutdownHook(dataSource);
    }

    private void registerShutdownHook(DataSource dataSource) {
      if (!SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
        return;
      }
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    // Best effort: the pool may already be closed at shutdown. The next detector
                    // run reinstalls idempotently regardless, so a missed drop never changes it.
                    try (Connection connection = dataSource.getConnection()) {
                      WriteAttrDetectorTrigger.uninstall(connection);
                      if (!connection.getAutoCommit()) {
                        connection.commit();
                      }
                    } catch (Exception ignored) {
                      // nothing readable can act on a shutdown-time failure
                    }
                  },
                  "writeattr-trigger-uninstall"));
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
