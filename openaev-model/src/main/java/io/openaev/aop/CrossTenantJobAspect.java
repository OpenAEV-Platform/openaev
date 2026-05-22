package io.openaev.aop;

import io.openaev.context.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Automatically enables cross-tenant mode for the duration of any {@code execute()} method on a
 * class annotated with {@link CrossTenantJob}. This ensures the Hibernate tenant filter is skipped
 * for all transactional calls within that job execution.
 *
 * <p>{@code @Order(1)} guarantees this aspect runs before {@link HibernateFilterTransactionAspect}
 * (Order 2), which matters when {@code execute()} is itself {@code @Transactional}.
 */
@Aspect
@Component
@Order(1)
public class CrossTenantJobAspect {

  @Around(
      "execution(* *(..)) && @within(io.openaev.aop.CrossTenantJob) "
          + "&& execution(public void execute(..))")
  public Object aroundCrossTenantJob(ProceedingJoinPoint joinPoint) throws Throwable {
    TenantContext.enableCrossTenant();
    try {
      return joinPoint.proceed();
    } finally {
      TenantContext.disableCrossTenant();
    }
  }
}
