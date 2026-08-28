package io.openaev.debug;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Wraps the auto-configured {@link DataSource} in a datasource-proxy for SQL logging. Created only
 * when debug mode is on; when off there is no proxy on the query path at all.
 */
public class DataSourceProxyBeanPostProcessor implements BeanPostProcessor {

  private static final Logger log = LoggerFactory.getLogger(DataSourceProxyBeanPostProcessor.class);

  private final MaskingSqlLoggingListener listener;

  public DataSourceProxyBeanPostProcessor(MaskingSqlLoggingListener listener) {
    this.listener = listener;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
      log.warn("Debug mode: wrapping datasource bean '{}' with SQL statement logging", beanName);
      return ProxyDataSourceBuilder.create(dataSource)
          .name("openaev-debug")
          .listener(listener)
          .build();
    }
    return bean;
  }
}
