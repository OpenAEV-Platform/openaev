package io.openaev.aop;

import io.openaev.service.InjectExpectationService;
import io.openaev.service.chaining.QueueChainingService;
import io.openaev.service.chaining.StepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowUpdateEventAspect {

  private final QueueChainingService queueChainingService;
  private final InjectExpectationService injectExpectationService;
  private final StepService stepService;

  private final ExpressionParser parser = new SpelExpressionParser();

  @After("@annotation(annotation)")
  public void afterEventProcessed(JoinPoint joinPoint, WorkflowUpdateEvent annotation) {

    String injectIdSPEL = annotation.injectId();
    String expectationIdsSPEL = annotation.expectationIds();

    boolean hasInjectId = StringUtils.isNotBlank(injectIdSPEL);
    boolean hasExpectation = StringUtils.isNotBlank(expectationIdsSPEL);

    if (hasInjectId == hasExpectation) {
      throw new IllegalStateException(
        "Annotation @WorkflowUpdateEvent on " +
          joinPoint.getSignature().toShortString() +
          " must set exactly one of injectId or expectationTracesIds"
      );
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
  }

  /**
   * Send a workflow update event related to the given inject to the queue
   *
   * @param context      the SPEL evaluation context
   * @param injectIdSPEL the SPEL expression to fetch the injectId from the request
   */
  private void handleInjectIdParam(EvaluationContext context, String injectIdSPEL) {
    String injectId = "";
    Expression exp = parser.parseExpression(injectIdSPEL);
    injectId =
      exp.getValue(context) != null
        ? Objects.requireNonNull(exp.getValue(context)).toString()
        : "";

    if (!injectId.isEmpty()) {
      Optional<String> stepId = stepService.findStepIdByInjectId(injectId);
      if (stepId.isPresent()) {
        try {
          queueChainingService.updateStep(stepId.get());
        } catch (IOException e) {
          // TODO: exception management
          throw new RuntimeException(e);
        }
      }
    }
  }

  /**
   * Send a workflow update event related to all the injects related to the given expectation IDs to the queue
   *
   * @param context             the SPEL evaluation context
   * @param expectationIDsdSPEL the SPEL expression to fetch the injectId from the request
   */
  private void handleExpectationTracesParam(EvaluationContext context, String expectationIDsdSPEL) {
    Expression exp = parser.parseExpression(expectationIDsdSPEL);
    Object expectationIdsFromSPEL = exp.getValue(context) != null
      ? Objects.requireNonNull(exp.getValue(context))
      : List.of();
    if (expectationIdsFromSPEL instanceof Collection<?> c) {
      Set<String> expectationIds = c.stream().map(Object::toString).collect(Collectors.toSet());
      Set<String> injectIds = injectExpectationService.findDistinctInjectIdsByInjectExpectationIds(expectationIds);
      injectIds.forEach(s -> {
        try {
          queueChainingService.updateStep(s);
        } catch (IOException e) {
          // TODO: exception management
          throw new RuntimeException(e);
        }
      });
    } else {
      throw new IllegalStateException("@WorkflowUpdateEvent.expectationIDsdSPEL must return a Collection");
    }
  }
}
