package io.openaev.debug;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

/**
 * Wires the debug-mode beans, gated by {@link DebugEnabledCondition}. When off (or refused in
 * production) the whole configuration backs off: no proxy, no extra per-request cost.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(DebugEnabledCondition.class)
public class DebugConfiguration {

  @Bean
  public DebugRuntimeState debugRuntimeState() {
    return new DebugRuntimeState();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public SensitiveDataMasker sensitiveDataMasker(DebugProperties properties) {
    return new SensitiveDataMasker(properties.getMasking());
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  @ConditionalOnProperty(
      prefix = "openaev.debug.sql",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public MaskingSqlLoggingListener maskingSqlLoggingListener(
      SensitiveDataMasker masker, DebugRuntimeState runtimeState, DebugProperties properties) {
    return new MaskingSqlLoggingListener(masker, runtimeState, properties.getSql());
  }

  /** {@code static} so the post-processor is created early enough to wrap the datasource. */
  @Bean
  @ConditionalOnProperty(
      prefix = "openaev.debug.sql",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public static DataSourceProxyBeanPostProcessor dataSourceProxyBeanPostProcessor(
      MaskingSqlLoggingListener listener) {
    return new DataSourceProxyBeanPostProcessor(listener);
  }

  /** ORM summary / N+1 detection; rides on the SQL proxy, gated by {@code sql.enabled}. */
  @Bean
  @ConditionalOnProperty(
      prefix = "openaev.debug.sql",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public OrmInsightFilter ormInsightFilter(
      SensitiveDataMasker masker, DebugRuntimeState runtimeState, DebugUserSource userSource) {
    return new OrmInsightFilter(masker, runtimeState, userSource);
  }

  @Bean
  public JfrRecordingManager jfrRecordingManager(DebugProperties properties) {
    return new JfrRecordingManager(properties.getOutputDir(), properties.getJfr());
  }

  /** Routes the verbose SQL log to a rotated file (off the console). Gated like SQL logging. */
  @Bean
  @ConditionalOnProperty(
      prefix = "openaev.debug.sql",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public DebugSqlLogFileConfigurer debugSqlLogFileConfigurer(DebugProperties properties) {
    return new DebugSqlLogFileConfigurer(
        properties.getOutputDir(), properties.getSql(), properties.getOrm().isSummaryToFile());
  }

  @Bean
  public DebugTenantSource debugTenantSource() {
    return new DebugTenantSource();
  }

  @Bean
  public DebugTenantMdcInterceptor debugTenantMdcInterceptor(DebugTenantSource tenantSource) {
    return new DebugTenantMdcInterceptor(tenantSource);
  }

  @Bean
  public DebugUserSource debugUserSource() {
    return new DebugUserSource();
  }

  @Bean
  public DebugUserMdcInterceptor debugUserMdcInterceptor(DebugUserSource userSource) {
    return new DebugUserMdcInterceptor(userSource);
  }

  @Bean
  public DebugWebMvcConfigurer debugWebMvcConfigurer(
      DebugTenantMdcInterceptor tenantMdcInterceptor, DebugUserMdcInterceptor userMdcInterceptor) {
    return new DebugWebMvcConfigurer(tenantMdcInterceptor, userMdcInterceptor);
  }

  @Bean
  public DebugModeManager debugModeManager(
      DebugProperties properties,
      JfrRecordingManager jfrRecordingManager,
      DebugRuntimeState runtimeState,
      @Value("${pyroscope.agent.enabled:false}") boolean pyroscopeEnabled) {
    return new DebugModeManager(properties, jfrRecordingManager, runtimeState, pyroscopeEnabled);
  }
}
