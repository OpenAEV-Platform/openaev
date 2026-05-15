package io.openaev.aop;

import io.openaev.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Aspect that enables RLS bypass for methods annotated with {@link BypassRls}. Sets the bypass flag
 * on the current thread before execution and always clears it afterwards.
 *
 * <p>The {@link Order} is set to {@code Ordered.LOWEST_PRECEDENCE - 2} so that this aspect runs
 * <b>before</b> {@code @Transactional} (which defaults to {@code LOWEST_PRECEDENCE}). This is
 * critical because {@link io.openaev.config.TenantAwareDataSourceConfig} checks the bypass flag at
 * connection-checkout time: if the flag is not yet set when the transaction opens, the connection
 * is obtained under the restricted {@code openaev_app} role and PostgreSQL Row-Level Security is
 * enforced, causing spurious policy violations.
 */
@Aspect
@Component
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE - 2)
public class BypassRlsAspect {

  @Around("@annotation(io.openaev.aop.BypassRls)")
  public Object aroundBypassRls(ProceedingJoinPoint joinPoint) throws Throwable {
    TenantContext.setRlsBypass();
    try {
      log.debug("RLS bypass enabled for {}", joinPoint.getSignature().toShortString());
      return joinPoint.proceed();
    } finally {
      TenantContext.clearRlsBypass();
    }
  }
}
