package io.openaev.ratelimit.aop;

import io.openaev.ratelimit.model.RateLimitedPrincipal;
import io.openaev.ratelimit.service.RateLimitService;
import io.openaev.ratelimit.store.request.LimitConsumptionRequest;
import io.openaev.ratelimit.store.request.LimitSpecification;
import io.openaev.service.UserService;
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
  private final UserService userService;

  @Before("@annotation(rateLimit)")
  public void applyRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();

    LimitSpecification spec = new LimitSpecification(rateLimit.rps());
    LimitConsumptionRequest lcr =
        new LimitConsumptionRequest(
            RateLimitedPrincipal.fromUser(userService.currentUserOrNull()),
            String.valueOf(
                Objects.hash(
                    signature.getDeclaringTypeName(),
                    signature.getName(),
                    Arrays.hashCode(signature.getParameterNames()))),
            spec);

    if (rateLimitService.consume(lcr).getIsRateLimited()) {
      throw new RuntimeException("RATE LIMIT");
    }
  }
}
