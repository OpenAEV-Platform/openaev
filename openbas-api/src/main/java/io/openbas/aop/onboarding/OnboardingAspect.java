package io.openbas.aop.onboarding;

import io.openbas.service.UserService;
import io.openbas.service.onboarding.OnboardingService;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OnboardingAspect {

  private final OnboardingService onboardingService;
  private final UserService userService;

  @AfterReturning("@annotation(io.openbas.aop.onboarding.Onboarding)")
  public void handleOnboardingStep(JoinPoint joinPoint) {
    try {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      Method method = signature.getMethod();

      Onboarding onboarding = method.getAnnotation(Onboarding.class);
      String step = onboarding.step();
      boolean allUsers = onboarding.allUsers();
      if (allUsers) {
        onboardingService.completeStepForAllUsers(step);
      } else {
        onboardingService.completeStep(userService.currentUser(), step);
      }
    } catch (Exception e) {
      log.error("[OnboardingAspect] Failed to process onboarding step", e);
    }
  }
}
