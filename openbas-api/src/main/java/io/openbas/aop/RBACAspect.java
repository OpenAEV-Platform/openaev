package io.openbas.aop;

import io.openbas.config.SessionHelper;
import io.openbas.database.model.User;
import io.openbas.service.PermissionService;
import io.openbas.service.UserService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RBACAspect {

  private final PermissionService permissionService;
  private final UserService userService;

  private final ExpressionParser parser = new SpelExpressionParser();

  @Before("@annotation(rbac)")
  public void methodRBACVerification(JoinPoint joinPoint, RBAC rbac)
      throws AuthenticationException {
    if (rbac.skipRBAC()) {
      // If RBAC is disabled, skip the verification
      return;
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

    // Evaluate SpEL expressions to retrieve the resource ID if present
    String resourceId = "";
    if (!rbac.resourceId().isEmpty()) {
      Expression exp = parser.parseExpression(rbac.resourceId());
      resourceId =
          exp.getValue(context) != null
              ? Objects.requireNonNull(exp.getValue(context)).toString()
              : "";
    }

    // Retrieve principal from session or security context
    User principal = null;
    try {
      // Attempt to retrieve the principal from the security context
      principal = userService.currentUser();
    } catch (Exception e) {
      log.warn(String.format("Error retrieving current user: %s", e.getMessage()), e);
    }
    if (principal == null) {
      throw new AuthenticationException(
          "Access denied for user " + SessionHelper.currentUser().getId()) {};
    }

    // Perform your RBAC check with the extracted value
    boolean allowed =
        permissionService.hasPermission(
            principal, resourceId, rbac.resourceType(), rbac.actionPerformed());

    if (!allowed) {
      log.warn(
          "Access denied for user: {} on resource: {} of type: {} and action: {}",
          principal.getId(),
          resourceId,
          rbac.resourceType(),
          rbac.actionPerformed());
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Access denied for user: " + principal.getName()) {};
    }
  }
}
