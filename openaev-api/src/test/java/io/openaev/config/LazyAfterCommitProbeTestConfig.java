package io.openaev.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Wires the lazy-after-commit probe into one test context: {@link LazyAfterCommitProbeListener} on
 * the datasource, {@link LazyCollectionInitTracker} on the session factory, {@link
 * LazySerializationRecorder} on the object mapper the HTTP responses are written with, and {@link
 * LazyProbeRequestIsolation} on MockMvc. Import it on a test that wants the probe; a whole run gets
 * it from {@link LazyAfterCommitProbeContextCustomizerFactory} instead.
 */
@TestConfiguration
public class LazyAfterCommitProbeTestConfig {

  @Bean
  static BeanPostProcessor lazyAfterCommitProbeWiring(Environment environment) {
    return probeWiring(environment);
  }

  @Bean
  MockMvcBuilderCustomizer lazyAfterCommitProbeRequestIsolation(EntityManager entityManager) {
    return new LazyProbeRequestIsolation(() -> entityManager);
  }

  /**
   * The post-processor both entry points install. The raw datasource is captured as it passes
   * through, so the probe can replay a statement on a connection it owns.
   */
  static BeanPostProcessor probeWiring(Environment environment) {
    DataSource[] raw = new DataSource[1];
    LazyAfterCommitProbeListener listener =
        new LazyAfterCommitProbeListener(environment, () -> raw[0]);
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof EntityManagerFactory entityManagerFactory) {
          LazyCollectionInitTracker.install(entityManagerFactory);
          return bean;
        }
        if (bean instanceof ObjectMapper objectMapper) {
          objectMapper.registerModule(LazySerializationRecorder.module());
          return bean;
        }
        if (bean instanceof DataSource dataSource && raw[0] == null) {
          raw[0] = dataSource instanceof ProxyDataSource proxy ? proxy.getDataSource() : dataSource;
        }
        return DetectorDataSourceProxies.wrapOrChain(bean, "lazy-after-commit-probe", listener);
      }
    };
  }
}
