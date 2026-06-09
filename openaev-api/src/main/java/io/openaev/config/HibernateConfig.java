package io.openaev.config;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hibernate configuration that registers the {@link TenantStatementInspector}.
 *
 * <p>The inspector intercepts all SQL statements produced by Hibernate (JPA queries, JPQL, and
 * native queries) and rewrites them to add tenant-scoped {@code WHERE tenant_id = ?} clauses when a
 * tenant context is active.
 */
@Configuration
@RequiredArgsConstructor
public class HibernateConfig {

  private final TenantStatementInspector tenantStatementInspector;

  @Bean
  public HibernatePropertiesCustomizer tenantStatementInspectorCustomizer() {
    return (Map<String, Object> properties) ->
        properties.put(AvailableSettings.STATEMENT_INSPECTOR, tenantStatementInspector);
  }
}
