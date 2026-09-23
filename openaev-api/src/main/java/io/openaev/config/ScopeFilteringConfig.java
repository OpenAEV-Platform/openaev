package io.openaev.config;

import java.util.List;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Installs the single statement inspector Hibernate runs, composed of every scope dimension.
 *
 * <p>Hibernate accepts exactly one {@link AvailableSettings#STATEMENT_INSPECTOR}, so marking cannot
 * be a second, independent inspector: it would silently displace tenant isolation. Both dimensions
 * are therefore folded into one {@link ScopeStatementInspector}, which ANDs their predicates on the
 * tables they both cover.
 *
 * <p>The dimensions are listed explicitly rather than collected from the context so their order —
 * and hence the emitted SQL — is deterministic, and so that adding a dimension is a visible
 * decision here rather than a side effect of declaring a bean.
 */
@Configuration
public class ScopeFilteringConfig {

  @Bean
  public ScopeStatementInspector scopeStatementInspector(
      TenantDimension tenantDimension, MarkingDimension markingDimension) {
    return new ScopeStatementInspector(List.of(tenantDimension, markingDimension));
  }

  @Bean
  public HibernatePropertiesCustomizer scopeStatementInspectorCustomizer(
      ScopeStatementInspector inspector) {
    // putIfAbsent, not put: a test that wires its own statement_inspector (the capture probe) keeps
    // it; production sets none, so ours is installed. The trade-off is that any other inspector set
    // ahead of ours would silently displace it; TenantFilteringConfigTest pins ours as the one
    // Hibernate runs, so that regression fails the build rather than disabling isolation silently.
    return properties -> properties.putIfAbsent(AvailableSettings.STATEMENT_INSPECTOR, inspector);
  }
}
