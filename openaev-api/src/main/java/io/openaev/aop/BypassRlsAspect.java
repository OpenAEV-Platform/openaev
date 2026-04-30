package io.openaev.aop;

import io.openaev.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Aspect that enables RLS bypass for methods annotated with {@link BypassRls}. Sets the bypass flag
 * on the current thread before execution and always clears it afterwards.
 */
@Aspect
@Component
@Slf4j
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
