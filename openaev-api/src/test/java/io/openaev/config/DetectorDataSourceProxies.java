package io.openaev.config;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

/**
 * Shared datasource-proxy wiring for the test-scope detectors (fail-closed and write-attribution).
 *
 * <p>Every detector installs a {@link QueryExecutionListener} on the auto-configured {@link
 * DataSource} through a bean post-processor. When two detectors (or a detector and the debug-mode
 * SQL logger) are enabled in the same JVM, their post-processors run one after another on the same
 * bean. The naive shape - wrap only when the bean is not already a {@link ProxyDataSource}, skip
 * otherwise - lets whichever post-processor runs first win and silently drops every later listener:
 * the second detector then observes nothing, a safety net that looks armed but sees no statement.
 *
 * <p>This helper makes the wrappers coexist: the first one to see the raw datasource wraps it, and
 * every later one adds its listener to that single proxy's chain, so each listener sees every
 * statement it would have seen alone. Adding is idempotent per listener class, so a detector wired
 * both suite-wide and through an {@code @Import} on one test is installed once, not twice.
 */
final class DetectorDataSourceProxies {

  private DetectorDataSourceProxies() {}

  /**
   * Returns a {@link ProxyDataSource} carrying {@code listener}, reusing any proxy an earlier
   * wrapper already installed rather than replacing it. Non-datasource beans are returned
   * untouched.
   *
   * @param bean the bean a post-processor is inspecting
   * @param proxyName the datasource name to give a freshly created proxy
   * @param listener the detector listener to install
   */
  static Object wrapOrChain(Object bean, String proxyName, QueryExecutionListener listener) {
    if (bean instanceof ProxyDataSource proxy) {
      addListenerOnce(proxy, listener);
      return proxy;
    }
    if (bean instanceof DataSource dataSource) {
      return ProxyDataSourceBuilder.create(dataSource).name(proxyName).listener(listener).build();
    }
    return bean;
  }

  private static void addListenerOnce(ProxyDataSource proxy, QueryExecutionListener listener) {
    boolean alreadyInstalled =
        proxy.getProxyConfig().getQueryListener().getListeners().stream()
            .anyMatch(existing -> existing.getClass() == listener.getClass());
    if (!alreadyInstalled) {
      proxy.addListener(listener);
    }
  }
}
