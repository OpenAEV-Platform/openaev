package io.openaev.aop;

import io.openaev.context.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Enables the Hibernate {@code tenantFilter} before each {@code @Transactional} method, scoping
 * JPQL / Criteria queries by tenant.
 *
 * <p>Native SQL queries must manually include {@code WHERE tenant_id = :tenantId} since the
 * Hibernate filter does not apply to native queries.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class HibernateFilterTransactionAspect {

  private final EntityManager entityManager;

  @Before(
      "@annotation(org.springframework.transaction.annotation.Transactional) || "
          + "@annotation(jakarta.transaction.Transactional)")
  public void enableFilters() {
    String tenantId = TenantContext.getCurrentTenant();
    Session session = entityManager.unwrap(Session.class);

    // Hibernate filter — scopes JPQL / Criteria / derived queries
    session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
  }
}
