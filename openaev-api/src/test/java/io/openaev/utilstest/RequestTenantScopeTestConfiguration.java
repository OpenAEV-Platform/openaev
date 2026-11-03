package io.openaev.utilstest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.servlet.Filter;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Clears the tenant scope at the start of every MockMvc request. A test transaction spans the whole
 * test, so without this a request would inherit the scope set by {@link
 * DefaultTenantScopeTestListener} (or by a previous request) and TenantScopeTransactionAspect would
 * refuse to switch it to the request's own tenants. In production each request has its own
 * transaction and always starts unscoped; this restores that starting point.
 */
@Configuration
public class RequestTenantScopeTestConfiguration {

  @Bean
  MockMvcBuilderCustomizer requestTenantScopeReset(EntityManager entityManager) {
    Filter resetScope =
        (request, response, chain) -> {
          if (TransactionSynchronizationManager.isActualTransactionActive()) {
            entityManager
                .createNativeQuery("SELECT set_config('app.current_tenants', '', true)")
                .setFlushMode(FlushModeType.COMMIT)
                .getSingleResult();
          }
          chain.doFilter(request, response);
        };
    return builder -> builder.addFilters(resetScope);
  }
}
