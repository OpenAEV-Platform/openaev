package io.openaev.ratelimit.aop;

import io.openaev.ratelimit.service.RateLimitService;
import io.openaev.ratelimit.store.request.LimitConsumptionRequest;
import io.openaev.ratelimit.store.request.LimitSpecification;
import io.openaev.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {
  private final RateLimitService rateLimitService;
  private final UserService userService;

  @Before("@annotation(rateLimit)")
  public void applyRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
    LimitSpecification spec =
        new LimitSpecification(rateLimit.defaultRps(), rateLimit.authenticatedRps());
    LimitConsumptionRequest lcr =
        new LimitConsumptionRequest(userService.currentUser(), "haha", spec);

    if (rateLimitService.consume(lcr).getIsRateLimited()) {
      throw new RuntimeException("RATE LIMIT");
    }
  }
}
