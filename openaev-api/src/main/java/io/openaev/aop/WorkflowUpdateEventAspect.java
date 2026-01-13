package io.openaev.aop;

import io.openaev.service.chaining.QueueChainingService;
import io.openaev.service.chaining.StepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowUpdateEventAspect {

  private final QueueChainingService queueChainingService;
  private final StepService stepService;

  private final ExpressionParser parser = new SpelExpressionParser();

  @After("@annotation(updateEvent)")
  public void afterEventProcessed(JoinPoint joinPoint, WorkflowUpdateEvent updateEvent) {

    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String[] parameterNames = signature.getParameterNames();
    Object[] args = joinPoint.getArgs();
    Map<String, Object> paramMap;
    if (parameterNames == null || parameterNames.length == 0) {
      paramMap = Map.of();
    } else {
      paramMap = new HashMap<>();
      for (int i = 0; i < parameterNames.length; i++) {
        paramMap.put(parameterNames[i], args[i]);
      }
    }
    Method method = signature.getMethod();

    // Create SpEL evaluation context to retrieve the resource ID if it exists
    EvaluationContext context = new StandardEvaluationContext();

    // Add all method parameters to context
    for (int i = 0; i < parameterNames.length; i++) {
      context.setVariable(parameterNames[i], args[i]);
    }

    // Evaluate SpEL expressions to retrieve the inject ID if present
    String injectId = "";
    if (!updateEvent.injectId().isEmpty()) {
      Expression exp = parser.parseExpression(updateEvent.injectId());
      injectId =
        exp.getValue(context) != null
          ? Objects.requireNonNull(exp.getValue(context)).toString()
          : "";
    }

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
}
