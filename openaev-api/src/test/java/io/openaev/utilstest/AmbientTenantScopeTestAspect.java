package io.openaev.utilstest;

import io.openaev.context.TxCtx;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lets a test call a scoped {@code @Transactional} service directly, the way a request would. The
 * test transaction already carries the ambient scope set by {@link DefaultTenantScopeTestListener},
 * and TenantScopeTransactionAspect refuses to change a scope once set. So right before that aspect
 * runs, the ambient scope is cleared, but only while it is still the ambient one: once a method has
 * set its own scope, nested calls see the production guard unchanged.
 */
@Aspect
@Component
@RequiredArgsConstructor
// Inside the transaction advisor (LP-4), just outside TenantScopeTransactionAspect (LP-2).
@Order(Ordered.LOWEST_PRECEDENCE - 3)
public class AmbientTenantScopeTestAspect {

  private final EntityManager entityManager;

  @Before(
      "@annotation(org.springframework.transaction.annotation.Transactional) || "
          + "@annotation(jakarta.transaction.Transactional)")
  public void clearAmbientScope(JoinPoint joinPoint) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || Arrays.stream(joinPoint.getArgs()).noneMatch(TxCtx.class::isInstance)) {
      return;
    }
    entityManager
        .createNativeQuery(
            "SELECT CASE WHEN current_setting('app.current_tenants', true)"
                + " = current_setting('"
                + DefaultTenantScopeTestListener.AMBIENT_SCOPE_SETTING
                + "', true) THEN set_config('app.current_tenants', '', true) END")
        .setFlushMode(FlushModeType.COMMIT)
        .getSingleResult();
  }
}
