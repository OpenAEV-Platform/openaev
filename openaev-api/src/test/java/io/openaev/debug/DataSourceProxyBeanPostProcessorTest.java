package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The debug-mode SQL logger must chain onto a datasource a test-scope detector already proxied, not
 * skip itself (only debug mode's own logging would then be silently unarmed), and must not install
 * itself twice on a re-entrant post-process. Companion of {@link
 * io.openaev.config.DetectorDataSourceProxies}, whose test-side {@code addListenerOnce} guards the
 * same question for the write-attribution and fail-closed detectors; both use exact-class identity
 * to decide "already chained" so the two converge (see this class's javadoc).
 */
@DisplayName("DataSourceProxyBeanPostProcessor (debug-mode SQL logger chaining)")
class DataSourceProxyBeanPostProcessorTest {

  private static MaskingSqlLoggingListener newListener() {
    return new MaskingSqlLoggingListener(
        mock(SensitiveDataMasker.class), mock(DebugRuntimeState.class), new DebugProperties.Sql());
  }

  @Test
  @DisplayName("wraps a raw datasource bean with a proxy carrying the listener")
  void given_rawDataSource_should_wrapWithTheListener() {
    DataSourceProxyBeanPostProcessor processor =
        new DataSourceProxyBeanPostProcessor(newListener());
    DataSource raw = mock(DataSource.class);

    Object result = processor.postProcessAfterInitialization(raw, "dataSource");

    assertThat(result).isInstanceOf(ProxyDataSource.class);
    ProxyDataSource proxy = (ProxyDataSource) result;
    assertThat(proxy.getProxyConfig().getQueryListener().getListeners())
        .hasSize(1)
        .allSatisfy(l -> assertThat(l.getClass()).isEqualTo(MaskingSqlLoggingListener.class));
  }

  @Test
  @DisplayName("chains onto a datasource a test-scope detector already proxied")
  void given_alreadyProxiedDataSource_should_chainOntoIt() {
    DataSource raw = mock(DataSource.class);
    ProxyDataSource alreadyProxied =
        ProxyDataSourceBuilder.create(raw).name("test-detector").build();
    DataSourceProxyBeanPostProcessor processor =
        new DataSourceProxyBeanPostProcessor(newListener());

    Object result = processor.postProcessAfterInitialization(alreadyProxied, "dataSource");

    assertThat(result).isSameAs(alreadyProxied);
    assertThat(alreadyProxied.getProxyConfig().getQueryListener().getListeners())
        .filteredOn(l -> l.getClass() == MaskingSqlLoggingListener.class)
        .hasSize(1);
  }

  @Test
  @DisplayName("does not install a second instance when the listener is already chained")
  void given_listenerAlreadyChained_should_notInstallTwice() {
    DataSource raw = mock(DataSource.class);
    MaskingSqlLoggingListener first = newListener();
    ProxyDataSource alreadyProxied =
        ProxyDataSourceBuilder.create(raw).name("debug").listener(first).build();
    DataSourceProxyBeanPostProcessor processor =
        new DataSourceProxyBeanPostProcessor(newListener());

    processor.postProcessAfterInitialization(alreadyProxied, "dataSource");

    assertThat(alreadyProxied.getProxyConfig().getQueryListener().getListeners())
        .filteredOn(l -> l.getClass() == MaskingSqlLoggingListener.class)
        .hasSize(1);
  }
}
