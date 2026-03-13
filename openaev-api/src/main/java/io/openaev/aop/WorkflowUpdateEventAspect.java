package io.openaev.aop;

import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.chaining.QueueChainingService;
import io.openaev.service.chaining.StepService;
import java.io.IOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * IDs from method parameters, then sends update events to the workflow external update queue.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowUpdateEventAspect {

  /**
   * This list will contain IDs of any event that couldn't be sent due to a problem with the queue
   * (most likely a network error, or the queue system being down)
   */
  private Set<String> unsentEventsCache = new HashSet<>();

  private final PreviewFeatureService previewFeatureService;

  private final QueueChainingService queueChainingService;
  private final StepService stepService;

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
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)) {
      // No chaining enabled, does nothing
      return;
    }

    String injectIdSPEL = annotation.injectId();
    String expectationIdsSPEL = annotation.expectationIds();

    boolean hasInjectId = StringUtils.isNotBlank(injectIdSPEL);
    boolean hasExpectation = StringUtils.isNotBlank(expectationIdsSPEL);

    if (hasInjectId == hasExpectation) {
      throw new IllegalStateException(
          "Annotation @WorkflowUpdateEvent on "
              + joinPoint.getSignature().toShortString()
              + " must set exactly one of injectId or expectationIds");
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

    if (hasInjectId) {
      this.handleInjectIdParam(context, injectIdSPEL);
    } else {
      this.handleExpectationTracesParam(context, expectationIdsSPEL);
    }

    // Retry events that are stored in the unsent cache due to previous errors
    sendEvents(unsentEventsCache);
  }

  /**
   * Send a workflow update event related to the given inject to the queue, and update the unsent
   * events cache if an error occurs
   *
   * @param context the SpEL evaluation context
   * @param injectIdSPEL the SpEL expression to fetch the injectId from the request
   */
  private void handleInjectIdParam(EvaluationContext context, String injectIdSPEL) {
    Expression exp = parser.parseExpression(injectIdSPEL);
    String injectId =
        exp.getValue(context) != null
            ? Objects.requireNonNull(exp.getValue(context)).toString()
            : "";

    if (!injectId.isEmpty()) {
      String stepId = stepService.findStepIdByInjectId(injectId);
      try {
        queueChainingService.updateStep(stepId);
      } catch (IOException e) {
        // In case an error occurs, we store the inject in the unsent event cache to be retried
        // later, when other events will be sent
        unsentEventsCache.add(stepId);
      }
    }
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

    Set<String> stepIds = stepService.findStepIdsByExpectationIds(expectationIds);
    sendEvents(stepIds);
  }

  /**
   * Send a list of events in the queue, and update the unsent events cache if an error occurs
   *
   * @param stepIds step IDs to notify with an event
   */
  private void sendEvents(Set<String> stepIds) {
    if (stepIds.isEmpty()) {
      return;
    }
    Set<String> remainingUnsetEvents = new HashSet<>(stepIds);
    try {
      for (String stepId : stepIds) {
        queueChainingService.updateStep(stepId);
        remainingUnsetEvents.remove(stepId);
      }
      unsentEventsCache.clear();
    } catch (IOException e) {
      // If a fail occurs, no need to continue the loop, just keep the remaining events in the
      // unsent events cache to be retried later, when other events will be sent
      unsentEventsCache = remainingUnsetEvents;
    }
  }
}
