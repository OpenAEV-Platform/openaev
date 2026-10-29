package io.openaev.config;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Wires the write-attribution detector into a single test context: wraps the auto-configured {@link
 * DataSource} with a datasource-proxy carrying {@link WriteAttrDetectorListener}, so the real
 * inspector still rewrites SQL while the listener reads the trigger's warnings. Import on a
 * real-stack test that installs the trigger ({@link WriteAttrDetectorTrigger}) and wants the
 * detector watching writes. Mirrors {@link FailClosedDetectorTestConfig}.
 *
 * <p>The classifier is resolved lazily from the context so the entity model and datasource, needed
 * only at query time, are not touched while bean post-processors are still being registered.
 */
@TestConfiguration
public class WriteAttrDetectorTestConfig {

  @Bean
  static BeanPostProcessor writeAttrDetectorDataSourceWrapper(
      ApplicationContext applicationContext) {
    WriteAttrDetectorListener listener =
        new WriteAttrDetectorListener(
            WriteAttrDetectorListener.classifierFrom(
                () -> applicationContext.getBean(EntityManagerFactory.class),
                () -> applicationContext.getBean(DataSource.class)));
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        return DetectorDataSourceProxies.wrapOrChain(bean, "writeattr-detector", listener);
      }
    };
  }

  /**
   * Registers the ask-time attribution listeners on this context's SessionFactory once it is
   * refreshed, so a test that imports this config gets the deferred-JPA-write attribution as well
   * as the proxy DataSource, without needing the suite-wide detector flag. Registration is
   * idempotent per factory.
   */
  @Bean
  static ApplicationListener<ContextRefreshedEvent> writeAttrHibernateListenerRegistrar(
      ApplicationContext applicationContext) {
    return event ->
        WriteAttrHibernateListeners.register(
            applicationContext.getBean(EntityManagerFactory.class));
  }
}
