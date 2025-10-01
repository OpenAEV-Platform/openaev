package io.openaev.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Aspect
@Component
@ConditionalOnProperty(name = "logging.aspect.enabled", havingValue = "true", matchIfMissing = true)
public class LoggingAspect {

  public static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

  private static final long EXECUTION_TIME_THRESHOLD = 500;

  /**
   * This method uses Around advice which ensures that an advice can run before and after the method
   * execution, to and log the execution time of the method This advice will be applied to all the
   * method which are annotate with the annotation @LogExecutionTime
   */
  @Around("@annotation(io.openaev.aop.LogExecutionTime)")
  public Object methodTimeLogger(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    MethodSignature methodSignature = (MethodSignature) proceedingJoinPoint.getSignature();

    // Get intercepted method details
    String className = methodSignature.getDeclaringType().getSimpleName();
    String methodName = methodSignature.getName();

    // Measure method execution time
    StopWatch stopWatch = new StopWatch(className + "->" + methodName);
    stopWatch.start(methodName);
    Object result = proceedingJoinPoint.proceed();
    stopWatch.stop();

    long executionTime = stopWatch.getTotalTimeMillis();

    // Log method execution time
    if (logger.isInfoEnabled()) {
      logger.info(stopWatch.prettyPrint());
    }

    if (executionTime > EXECUTION_TIME_THRESHOLD) {
      logger.warn(
          "Execution of "
              + className
              + "."
              + methodName
              + " took "
              + executionTime
              + " ms, which exceeds the threshold of "
              + EXECUTION_TIME_THRESHOLD
              + " ms");
    }

    return result;
  }
}
