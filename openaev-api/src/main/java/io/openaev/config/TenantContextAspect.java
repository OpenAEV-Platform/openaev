package io.openaev.config;

import io.openaev.context.OperationState;
import io.openaev.context.TenantExecutionContext;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that automatically sets the {@link TenantExecutionContext} for any {@code @Service}
 * method that declares an {@link OperationState} parameter.
 *
 * <h3>Why AOP instead of explicit {@code TenantExecutionContext.run()} calls</h3>
 *
 * <p>Relying on developers to call {@code TenantExecutionContext.run()} around every repository
 * call is fragile:
 *
 * <ul>
 *   <li>Easy to forget, especially in parallel streams
 *   <li>Spreads infrastructure boilerplate into business logic
 *   <li>Cannot be enforced by the compiler
 * </ul>
 *
 * <p>With this aspect, the contract is simple: <em>declare {@link OperationState} as a method
 * parameter and the tenant context is set automatically</em>. This works correctly for parallel
 * streams because each invocation goes through the Spring proxy on its own thread:
 *
 * <pre>{@code
 * // No TenantExecutionContext.run() needed — works correctly in parallel
 * ids.parallelStream()
 *    .map(id -> documentService.document(operationState, id))
 *    .toList();
 * }</pre>
 *
 * <h3>Nesting</h3>
 *
 * <p>When a service method calls another service method, the aspect saves and restores the previous
 * tenant context, so nested calls are always correct.
 *
 * <h3>Scope</h3>
 *
 * <p>Only intercepts Spring beans annotated with {@code @Service}. Repository calls directly from
 * non-service beans (e.g. schedulers, event listeners) must still use {@link
 * TenantExecutionContext#run} or {@link TenantExecutionContext#set} explicitly.
 */
@Aspect
@Component
@Slf4j
public class TenantContextAspect {

  /**
   * Intercepts any method on a {@code @Service} bean that has at least one {@link OperationState}
   * parameter. Sets the tenant context from that parameter for the duration of the method call,
   * saving and restoring any previously active context (safe to nest).
   */
  @Around("@within(org.springframework.stereotype.Service)")
  public Object propagateTenantContext(ProceedingJoinPoint joinPoint) throws Throwable {
    OperationState operationState = findOperationState(joinPoint.getArgs());

    if (operationState == null || !operationState.hasTenant()) {
      // No OperationState in args, or empty tenant list — proceed without modifying context
      return joinPoint.proceed();
    }

    // Save any previously active context (e.g. when a service calls another service)
    List<String> previous = TenantExecutionContext.get();
    try {
      TenantExecutionContext.set(operationState);
      return joinPoint.proceed();
    } finally {
      if (previous == null) {
        TenantExecutionContext.clear();
      } else {
        TenantExecutionContext.set(new OperationState(previous));
      }
    }
  }

  private OperationState findOperationState(Object[] args) {
    if (args == null) return null;
    return Arrays.stream(args)
        .filter(arg -> arg instanceof OperationState)
        .map(OperationState.class::cast)
        .findFirst()
        .orElse(null);
  }
}
