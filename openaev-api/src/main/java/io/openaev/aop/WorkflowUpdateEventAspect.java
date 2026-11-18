package io.openaev.aop;

import io.openaev.service.chaining.WorkflowUpdateEventPublisher;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * Aspect that intercepts methods annotated with {@link WorkflowUpdateEvent} to trigger workflow
 * updates in the chaining engine.
 *
 * <p>This aspect uses SpEL expressions defined in the annotation to extract inject or expectation
 * IDs from method parameters, then hands them to the {@link WorkflowUpdateEventPublisher}, which
 * looks the steps up in the annotated method's transaction and sends the update events.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class WorkflowUpdateEventAspect {

  private final WorkflowUpdateEventPublisher workflowUpdateEventPublisher;

  private final ExpressionParser parser = new SpelExpressionParser();

  /**
   * Advice executed after methods annotated with {@link WorkflowUpdateEvent} returns. If an
   * exception is throw by the annotated method, this advice will not be called.
   *
   * <p>Extracts the inject ID or expectation IDs from method parameters using SpEL expressions
   * defined in the annotation, then dispatches workflow update events accordingly.
   *
   * @param joinPoint the join point providing access to the method signature and arguments
   * @param annotation the {@link WorkflowUpdateEvent} annotation containing SpEL expressions
   * @throws IllegalStateException if the annotation does not specify exactly one of injectId or
   *     expectationIds
   */
  @AfterReturning("@annotation(annotation)")
  public void afterEventProcessed(JoinPoint joinPoint, WorkflowUpdateEvent annotation) {
    String injectIdSPEL = annotation.injectId();
    String injectIdsSPEL = annotation.injectIds();
    String expectationIdsSPEL = annotation.expectationIds();

    long filledFields =
        Stream.of(injectIdSPEL, injectIdsSPEL, expectationIdsSPEL)
            .filter(StringUtils::isNotBlank)
            .count();

    if (filledFields != 1) {
      throw new IllegalStateException(
          "Annotation @WorkflowUpdateEvent on "
              + joinPoint.getSignature().toShortString()
              + " must set exactly one of injectId, injectIds or expectationIds");
    }

    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String[] parameterNames = signature.getParameterNames();
    Object[] args = joinPoint.getArgs();

    // Create SpEL evaluation context to retrieve the resource ID if it exists
    EvaluationContext context = new StandardEvaluationContext();

    // Add all method parameters to context
    for (int i = 0; i < parameterNames.length; i++) {
      context.setVariable(parameterNames[i], args[i]);
    }

    if (StringUtils.isNotBlank(injectIdSPEL)) {
      this.handleInjectIdParam(context, injectIdSPEL);
    } else if (StringUtils.isNotBlank(injectIdsSPEL)) {
      this.handleInjectIdsParam(context, injectIdsSPEL);
    } else {
      this.handleExpectationTracesParam(context, expectationIdsSPEL);
    }
  }

  /**
   * Send a workflow update event related to the given inject
   *
   * @param context the SpEL evaluation context
   * @param injectIdSPEL the SpEL expression to fetch the injectId from the request
   */
  private void handleInjectIdParam(EvaluationContext context, String injectIdSPEL) {
    Expression exp = parser.parseExpression(injectIdSPEL);
    Object injectId = exp.getValue(context);
    workflowUpdateEventPublisher.publishForInject(injectId != null ? injectId.toString() : null);
  }

  /**
   * Send workflow update events for a collection of inject IDs
   *
   * @param context the SpEL evaluation context
   * @param injectIdsSPEL the SpEL expression to fetch the list of injectIds from the request
   */
  private void handleInjectIdsParam(EvaluationContext context, String injectIdsSPEL) {
    Expression exp = parser.parseExpression(injectIdsSPEL);
    Object injectIdsFromSPEL =
        exp.getValue(context) != null ? Objects.requireNonNull(exp.getValue(context)) : null;

    Set<String> injectIds = new HashSet<>();
    if (injectIdsFromSPEL instanceof Collection<?> c) {
      c.stream().map(Object::toString).forEach(injectIds::add);
    } else if (injectIdsFromSPEL instanceof String injectId) {
      injectIds.add(injectId);
    } else {
      throw new IllegalStateException(
          "@WorkflowUpdateEvent.injectIds SpEL must return a Collection or a String");
    }

    workflowUpdateEventPublisher.publishForInjects(injectIds);
  }

  /**
   * Send a workflow update event related to all the injects related to the given expectation IDs to
   * the queue
   *
   * @param context the SpEL evaluation context
   * @param expectationIDsdSPEL the SpEL expression to fetch the injectId from the request
   */
  private void handleExpectationTracesParam(EvaluationContext context, String expectationIDsdSPEL) {
    Expression exp = parser.parseExpression(expectationIDsdSPEL);
    Object expectationIdsFromSPEL =
        exp.getValue(context) != null ? Objects.requireNonNull(exp.getValue(context)) : null;

    Set<String> expectationIds = new HashSet<>();
    if (expectationIdsFromSPEL instanceof Collection<?> c) {
      c.stream().map(Object::toString).forEach(expectationIds::add);
    } else if (expectationIdsFromSPEL instanceof String expectationId) {
      expectationIds.add(expectationId);
    } else {
      throw new IllegalStateException(
          "@WorkflowUpdateEvent.expectationIDsdSpEL must return a Collection or a String");
    }

    workflowUpdateEventPublisher.publishForExpectations(expectationIds);
  }
}
