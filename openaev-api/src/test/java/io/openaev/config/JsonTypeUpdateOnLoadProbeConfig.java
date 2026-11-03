package io.openaev.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link JsonTypeUpdateOnLoadRecorder} into a test context: wraps the auto-configured {@link
 * DataSource} with a datasource-proxy carrying the recorder, chaining onto any proxy another
 * detector already installed. Import on a real-stack test that wants to assert what a read actually
 * sent to the database.
 *
 * <p>The recorder is a single instance shared with the context so the test and the proxy agree on
 * the recording window.
 */
@TestConfiguration
public class JsonTypeUpdateOnLoadProbeConfig {

  private static final JsonTypeUpdateOnLoadRecorder RECORDER = new JsonTypeUpdateOnLoadRecorder();

  @Bean
  JsonTypeUpdateOnLoadRecorder jsonTypeUpdateOnLoadRecorder() {
    return RECORDER;
  }

  @Bean
  static BeanPostProcessor jsonTypeUpdateOnLoadDataSourceWrapper() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        return DetectorDataSourceProxies.wrapOrChain(bean, "jsontype-update-on-load", RECORDER);
      }
    };
  }
}
