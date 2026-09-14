package io.openaev.config;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Wires the write-attribution detector into a single test context: wraps the auto-configured {@link
 * DataSource} with a datasource-proxy carrying {@link WriteAttrDetectorListener}, so the real
 * inspector still rewrites SQL while the listener reads the trigger's warnings. Import on a
 * real-stack test that installs the trigger ({@link WriteAttrDetectorTrigger}) and wants the
 * detector watching writes. Mirrors {@link FailClosedDetectorTestConfig}.
 */
@TestConfiguration
public class WriteAttrDetectorTestConfig {

  @Bean
  static BeanPostProcessor writeAttrDetectorDataSourceWrapper() {
    WriteAttrDetectorListener listener = new WriteAttrDetectorListener();
    return new BeanPostProcessor() {
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
    };
  }
}
