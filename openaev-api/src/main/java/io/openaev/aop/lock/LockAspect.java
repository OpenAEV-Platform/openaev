package io.openaev.aop.lock;

// Spring Framework
import com.google.common.util.concurrent.Striped;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE - 1) // Execute before @Transactional
public class LockAspect {

  private final ConcurrentHashMap<LockResourceType, Striped<Lock>> lockStripes;
  private final SpelExpressionParser parser = new SpelExpressionParser();

  public LockAspect() {

    this.lockStripes = new ConcurrentHashMap<>();
    // Creates 4096 locks that are distributed across IDs
    // 1024 is the default number of locks, but we increase it to reduce contention for highly
    // concurrent scenarios
    // (example: user with 10000+ implants triggered by the same inject)
    for (LockResourceType type : LockResourceType.values()) {
      this.lockStripes.put(type, Striped.lock(type.stripes()));
    }

    log.info("Initialized LockAspect with stripe configurations");
  }

  @Around("@annotation(lockAnnotation)")
  public Object aroundLocked(ProceedingJoinPoint joinPoint, io.openaev.aop.lock.Lock lockAnnotation)
      throws Throwable {
    // If only one stripe, we just use the string as lock object
    Object lockKey = lockAnnotation.key();
    if (lockAnnotation.type().stripes() > 1) {
      // Extract lock key from SpEL expression
      lockKey = extractLockKey(joinPoint, lockAnnotation.key());
    }
    LockResourceType lockType = lockAnnotation.type();

    if (lockKey == null) {
      throw new IllegalArgumentException("Lock key evaluated to null");
    }

    Striped<Lock> striped = lockStripes.get(lockType);
    Lock lock = striped.get(lockKey);

    boolean acquired = false;

    try {
      if (lockAnnotation.timeout() > 0) {
        acquired = lock.tryLock(lockAnnotation.timeout(), TimeUnit.MILLISECONDS);
        if (!acquired) {
          if (lockAnnotation.skipIfLocked()) {
            log.warn(
                "Skipping execution - could not acquire lock for key: {} (type: {})",
                lockKey,
                lockType);
            return null;
          } else {
            throw new LockAcquisitionException(
                lockAnnotation.errorMessage() + " - key: " + lockKey);
          }
        }
      } else {
        lock.lock();
        acquired = true;
      }

      log.debug("Acquired lock for key: {} (type: {})", lockKey, lockType);
      return joinPoint.proceed();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LockAcquisitionException("Interrupted while acquiring lock", e);
    } finally {
      if (acquired) {
        lock.unlock();
        log.debug("Released lock for key: {} (type: {})", lockKey, lockType);
      }
    }
  }

  private Object extractLockKey(ProceedingJoinPoint joinPoint, String spelExpression) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Object[] args = joinPoint.getArgs();

    // Create evaluation context
    EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();

    // Add method parameters to context
    String[] paramNames = signature.getParameterNames();
    for (int i = 0; i < paramNames.length; i++) {
      context.setVariable(paramNames[i], args[i]);
    }

    // Parse and evaluate expression
    Expression expression = parser.parseExpression(spelExpression);
    return expression.getValue(context);
  }
}
