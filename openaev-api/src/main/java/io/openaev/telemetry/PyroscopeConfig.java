package io.openaev.telemetry;

import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.labels.v2.LabelsSet;
import io.pyroscope.labels.v2.Pyroscope;
import io.swagger.v3.oas.annotations.Operation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

@Aspect
@Component
@ConditionalOnProperty(prefix = "pyroscope.agent", name = "enabled")
public class PyroscopeConfig {

  Map<String, LabelsSet> mapLabelsSetByMethodSignature = new HashMap<>();

  private static final String SCHEDULER_JOBS_PACKAGE = "io.openaev.scheduler.jobs";
  private static final String ENDPOINTS_PACKAGE = "io.openaev.rest";

  /** The pointcut to use. Targets all method in the package and subpackages io.openaev.rest */
  @Pointcut("execution(* io.openaev.scheduler.jobs..*.execute(..))")
  public void allExecuteJobs() {}

  /** The pointcut to use. Targets all method in the package and subpackages io.openaev.rest */
  @Pointcut("execution(* io.openaev.rest..*.*(..))")
  public void allRESTMethods() {}

  /**
   * Method to add pyroscope labels to all rest api endpoints. The method will add labels only on
   * method that have annotation like @GetMapping, @PostMapping, ...
   *
   * @param proceedingJoinPoint the joinpoint
   * @return the object returned by the method
   * @throws Throwable in case of exception
   */
  @Around("allRESTMethods() || allExecuteJobs()")
  public Object addLabels(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    // We add labels only if pyroscope is started
    if (PyroscopeAgent.isStarted()) {
      // We're getting the method itself
      MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
      if (!mapLabelsSetByMethodSignature.containsKey(signature.toLongString())) {
        Method method = signature.getMethod();

        // We get the class name
        String className = signature.getDeclaringType().getCanonicalName();

        String type = "JOB";

        // If we're dealing with a rest endpoints
        if (signature.getDeclaringTypeName().startsWith(ENDPOINTS_PACKAGE)) {
          // We check if there are @GetMapping, @PostMapping, ... annotation to only add labels to
          // those
          boolean hasGetMapping = method.getAnnotation(GetMapping.class) != null;
          boolean hasPutMapping = method.getAnnotation(PutMapping.class) != null;
          boolean hasPostMapping = method.getAnnotation(PostMapping.class) != null;
          boolean hasDeleteMapping = method.getAnnotation(DeleteMapping.class) != null;
          boolean hasPatchMapping = method.getAnnotation(PatchMapping.class) != null;

          // If they do have those annotation, we will add labels
          if (hasGetMapping
              || hasPatchMapping
              || hasDeleteMapping
              || hasPutMapping
              || hasPostMapping) {
            // We get the type of endpoint we're in to add it as label
            if (hasGetMapping) {
              type = "GET";
            } else if (hasPostMapping) {
              type = "POST";
            } else if (hasPutMapping) {
              type = "PUT";
            } else if (hasDeleteMapping) {
              type = "DELETE";
            } else {
              type = "PATCH";
            }

            // We get the swagger operation annotation as it's easier to read than the name of
            // the
            // method itself
            Operation operationAnnotation = method.getAnnotation(Operation.class);
            mapLabelsSetByMethodSignature.put(
                signature.toLongString(),
                new LabelsSet(
                    "Class",
                    // We're removing the full name of the package for readibility
                    className.substring(className.lastIndexOf(".") + 1),
                    "Operation",
                    type,
                    "Method",
                    // If we can, we use the operation summary as the method name as it's more
                    // explicit
                    operationAnnotation != null
                        ? operationAnnotation.summary()
                        : method.getName()));
          } else {
            mapLabelsSetByMethodSignature.put(signature.toLongString(), null);
          }
        } else if (signature.getDeclaringTypeName().startsWith(SCHEDULER_JOBS_PACKAGE)) {
          // If we're dealing with a job
          mapLabelsSetByMethodSignature.put(
              signature.toLongString(),
              new LabelsSet(
                  "Class",
                  // We're removing the full name of the package for readibility
                  className.substring(className.lastIndexOf(".") + 1),
                  "Operation",
                  type,
                  "Method",
                  // Since we're dealing with a job, we use the method name
                  signature.toShortString()));
        } else {
          mapLabelsSetByMethodSignature.put(signature.toLongString(), null);
        }
      }
      if (mapLabelsSetByMethodSignature.get(signature.toLongString()) != null) {
        return Pyroscope.LabelsWrapper.run(
            mapLabelsSetByMethodSignature.get(signature.toLongString()),
            () -> {
              try {
                return proceedingJoinPoint.proceed();
              } catch (Throwable e) {
                throw new RuntimeException(e);
              }
            });
      }
    }
    return proceedingJoinPoint.proceed();
  }
}
