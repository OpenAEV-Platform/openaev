package io.openaev.ratelimit.aop;

import io.openaev.config.SessionHelper;
import io.openaev.ratelimit.config.RateLimitConfig;
import io.openaev.ratelimit.exception.RateLimitedException;
import io.openaev.ratelimit.model.RateLimitedPrincipal;
import io.openaev.ratelimit.service.RateLimitService;
import io.openaev.ratelimit.store.Limit;
import io.openaev.ratelimit.store.request.LimitConsumptionRequest;
import io.openaev.ratelimit.store.request.LimitSpecification;
import java.util.Arrays;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {
  private final RateLimitService rateLimitService;
  private final RateLimitConfig rateLimitConfig;

  @Before("this(io.openaev.rest.helper.RestBehavior)")
  public void applyDefaultRateLimit(JoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();

    // if the specific method is annotated, then abort; let the other aspect handle this.
    if (signature.getMethod().getDeclaredAnnotation(RateLimit.class) != null) return;

    doRateLimiting(rateLimitConfig.getAuthenticatedRps(), addressFromMethodSignature(signature));
  }

  @Before("@annotation(rateLimit)")
  public void applyExplicitRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    doRateLimiting(rateLimit.rps(), addressFromMethodSignature(signature));
  }

  private void doRateLimiting(Long rps, String address) {
    if (!rateLimitConfig.getEnabled()) return;

    LimitSpecification spec = new LimitSpecification(rps);
    LimitConsumptionRequest lcr =
        new LimitConsumptionRequest(
            new RateLimitedPrincipal(SessionHelper.currentUser().getId()), address, spec);

    Limit l = rateLimitService.consume(lcr);
    if (l.getIsRateLimited()) {
      throw new RateLimitedException("Rate limited.", l.getLimit(), l.getRemaining(), l.getReset());
    }
  }

  private String addressFromMethodSignature(MethodSignature signature) {
    return String.valueOf(
        Objects.hash(
            signature.getDeclaringTypeName(),
            signature.getName(),
            Arrays.hashCode(signature.getParameterNames())));
  }
}
