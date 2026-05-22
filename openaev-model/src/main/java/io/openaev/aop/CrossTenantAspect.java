package io.openaev.aop;

import io.openaev.context.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enables cross-tenant mode (disabling the Hibernate tenant filter) for:
 *
 * <ul>
 *   <li>All public methods on a class annotated with {@link CrossTenant}
 *   <li>Any method annotated with {@link CrossTenant}
 * </ul>
 *
 * <p>{@code @Order(1)} guarantees this aspect runs before {@link HibernateFilterTransactionAspect}
 * (Order 2), which matters when the target method is itself {@code @Transactional}.
 */
@Aspect
@Component
@Order(1)
public class CrossTenantAspect {

  @Around("@within(io.openaev.aop.CrossTenant) && execution(public * *(..))")
  public Object aroundCrossTenantClass(ProceedingJoinPoint joinPoint) throws Throwable {
    return executeInCrossTenantContext(joinPoint);
  }

  @Around("@annotation(io.openaev.aop.CrossTenant)")
  public Object aroundCrossTenantMethod(ProceedingJoinPoint joinPoint) throws Throwable {
    return executeInCrossTenantContext(joinPoint);
  }

  private Object executeInCrossTenantContext(ProceedingJoinPoint joinPoint) throws Throwable {
    if (TenantContext.isCrossTenant()) {
      return joinPoint.proceed();
    }
    TenantContext.enableCrossTenant();
    try {
      return joinPoint.proceed();
    } finally {
      TenantContext.disableCrossTenant();
    }
  }
}
