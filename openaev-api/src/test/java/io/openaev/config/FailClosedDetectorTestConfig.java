package io.openaev.config;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Wires the WS1 fail-closed detector into a test context: wraps the auto-configured {@link
 * DataSource} with a datasource-proxy carrying {@link FailClosedDetectorListener}, so the real
 * inspector still rewrites SQL (isolation stays real) while the listener observes every execution
 * and its live scope. Import on a real-stack test that activates a table via
 * {@code @TestPropertySource} and wants the detector watching it.
 */
@TestConfiguration
public class FailClosedDetectorTestConfig {

  @Bean
  static BeanPostProcessor failClosedDetectorDataSourceWrapper(Environment environment) {
    FailClosedDetectorListener listener = new FailClosedDetectorListener(environment);
    return new BeanPostProcessor() {
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
    };
  }
}
