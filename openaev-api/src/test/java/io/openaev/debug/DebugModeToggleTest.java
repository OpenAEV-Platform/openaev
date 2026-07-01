package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Proves the single toggle behaves as required:
 *
 * <ul>
 *   <li>off by default ? none of the debug beans exist and the datasource is NOT proxied, so there
 *       is no proxy on the query hot path and no parameter capture (the overhead-when-off proof);
 *   <li>on ? the datasource is wrapped and the debug beans are present.
 * </ul>
 */
@DisplayName("Debug mode toggle")
class DebugModeToggleTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          // Keep the toggle test free of JFR side effects and noisy timers, write any debug file
          // under the build dir, and clear the production barrier (covered separately below).
          .withPropertyValues(
              "openaev.debug.jfr.enabled=false",
              "openaev.debug.warning-interval=1h",
              "openaev.debug.allow-in-production=true",
              "openaev.debug.output-dir=target/debug-toggle-test");

  @Test
  @DisplayName("off by default: no debug beans, datasource not proxied")
  void disabledByDefault() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(DataSourceProxyBeanPostProcessor.class);
          assertThat(context).doesNotHaveBean(MaskingSqlLoggingListener.class);
          assertThat(context).doesNotHaveBean(JfrRecordingManager.class);
          assertThat(context).doesNotHaveBean(DebugModeManager.class);
          assertThat(context).doesNotHaveBean(SensitiveDataMasker.class);
          assertThat(context).doesNotHaveBean(OrmInsightFilter.class);
          assertThat(context).doesNotHaveBean(DebugSqlLogFileConfigurer.class);
          assertThat(context).doesNotHaveBean(DebugTenantMdcInterceptor.class);
          assertThat(context).doesNotHaveBean(DebugWebMvcConfigurer.class);
          assertThat(context.getBean(DataSource.class)).isNotInstanceOf(ProxyDataSource.class);
        });
  }

  @Test
  @DisplayName("explicitly disabled: datasource not proxied")
  void explicitlyDisabled() {
    runner
        .withPropertyValues("openaev.debug.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(DataSourceProxyBeanPostProcessor.class);
              assertThat(context.getBean(DataSource.class)).isNotInstanceOf(ProxyDataSource.class);
            });
  }

  @Test
  @DisplayName("on: datasource is proxied and debug beans are present")
  void enabled() {
    runner
        .withPropertyValues("openaev.debug.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(DataSourceProxyBeanPostProcessor.class);
              assertThat(context).hasSingleBean(MaskingSqlLoggingListener.class);
              assertThat(context).hasSingleBean(JfrRecordingManager.class);
              assertThat(context).hasSingleBean(DebugModeManager.class);
              assertThat(context).hasSingleBean(OrmInsightFilter.class);
              assertThat(context).hasSingleBean(DebugSqlLogFileConfigurer.class);
              assertThat(context).hasSingleBean(DebugTenantMdcInterceptor.class);
              assertThat(context).hasSingleBean(DebugWebMvcConfigurer.class);
              assertThat(context).hasSingleBean(DebugRuntimeState.class);
              assertThat(context.getBean(DataSource.class)).isInstanceOf(ProxyDataSource.class);
            });
  }

  @Test
  @DisplayName("production barrier: enabled but refused without the explicit override")
  void refusedInProductionWithoutOverride() {
    // A bare runner with no non-production profile and no override: production is assumed.
    new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues("openaev.debug.enabled=true", "openaev.debug.jfr.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(DebugModeManager.class);
              assertThat(context).doesNotHaveBean(DataSourceProxyBeanPostProcessor.class);
              assertThat(context.getBean(DataSource.class)).isNotInstanceOf(ProxyDataSource.class);
            });
  }

  @Test
  @DisplayName("production barrier: the explicit override lets it start")
  void allowedInProductionWithOverride() {
    new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "openaev.debug.enabled=true",
            "openaev.debug.allow-in-production=true",
            "openaev.debug.jfr.enabled=false",
            "openaev.debug.warning-interval=1h",
            "openaev.debug.output-dir=target/debug-toggle-test")
        .run(context -> assertThat(context).hasSingleBean(DebugModeManager.class));
  }

  @Test
  @DisplayName("sql disabled: no proxy/ORM/SQL-file, but tenant MDC and JFR still present")
  void sqlDisabled() {
    runner
        .withPropertyValues("openaev.debug.enabled=true", "openaev.debug.sql.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(DataSourceProxyBeanPostProcessor.class);
              assertThat(context).doesNotHaveBean(OrmInsightFilter.class);
              assertThat(context).doesNotHaveBean(DebugSqlLogFileConfigurer.class);
              assertThat(context.getBean(DataSource.class)).isNotInstanceOf(ProxyDataSource.class);
              assertThat(context).hasSingleBean(JfrRecordingManager.class);
              assertThat(context).hasSingleBean(DebugTenantMdcInterceptor.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(DebugProperties.class)
  @Import(DebugConfiguration.class)
  static class TestConfig {
    @Bean
    DataSource dataSource() {
      JdbcDataSource ds = new JdbcDataSource();
      ds.setURL("jdbc:h2:mem:toggle-" + System.nanoTime());
      ds.setUser("sa");
      return ds;
    }
  }
}
