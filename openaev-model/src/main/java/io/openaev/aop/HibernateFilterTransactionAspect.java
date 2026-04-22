package io.openaev.aop;

import io.openaev.context.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Enables both:
 *
 * <ul>
 *   <li>The Hibernate {@code tenantFilter} — scopes JPQL / Criteria queries by tenant
 *   <li>The PostgreSQL session variable {@code app.current_tenant} — used by Row-Level Security
 *       policies to scope native SQL queries by tenant
 * </ul>
 *
 * <p>Together these two mechanisms ensure tenant isolation for <em>all</em> query types.
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

    // PostgreSQL session variable — scopes native SQL via Row-Level Security.
    // Uses SET (session-scoped) rather than SET LOCAL (transaction-scoped) so that
    // the variable persists for all queries on this connection, not just those inside
    // an explicit @Transactional block. The value is overwritten on each request
    // by the same aspect before any query runs.
    session.doWork(
        connection ->
            connection
                .createStatement()
                .execute("SET app.current_tenant = '" + tenantId.replace("'", "''") + "'"));
  }
}
