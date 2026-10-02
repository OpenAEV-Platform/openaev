package io.openaev.aop;

import io.openaev.context.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enables the Hibernate {@code tenantFilter} before each {@code @Transactional} method, scoping
 * JPQL / Criteria queries by tenant.
 *
 * <p>Native SQL queries must manually include {@code WHERE tenant_id = :tenantId} since the
 * Hibernate filter does not apply to native queries.
 *
 * <p>Ordered inside the transaction advisor but <b>outside</b> the RBAC aspect: the permission
 * check loads entities of its own, so leaving this advice tied with it on {@code LOWEST_PRECEDENCE}
 * let an unfiltered read win the tie and hydrate the persistence context before the filter existed.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 3)
@RequiredArgsConstructor
public class HibernateFilterTransactionAspect {

  private final EntityManager entityManager;

  @Before("@annotation(org.springframework.transaction.annotation.Transactional)")
  public void enableFilters() {
    String tenantId = TenantContext.getCurrentTenant();
    Session session = entityManager.unwrap(Session.class);

    // Hibernate filter — scopes JPQL / Criteria / derived queries
    session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
  }
}
