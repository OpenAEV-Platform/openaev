package io.openaev.rest.security;

import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.UserRepository;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {

  private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
  private final UserRepository userRepository;
  private final ExerciseRepository exerciseRepository;

  private SecurityExpression securityExpression;

  // FIXME: note that if the security expression is not set at this point
  // FIXME: a new one is created with the ambient identity
  public SecurityExpression getSecurityExpression() {
    if (securityExpression == null) {
      securityExpression =
          createSecurityExpression(
              SecurityContextHolder.getContext().getAuthentication(),
              userRepository,
              exerciseRepository,
              getPermissionEvaluator(),
              this.trustResolver,
              getRoleHierarchy());
    }
    return securityExpression;
  }

  public SecurityExpressionHandler(
      final UserRepository userRepository, final ExerciseRepository exerciseRepository) {
    this.userRepository = userRepository;
    this.exerciseRepository = exerciseRepository;
  }

  private SecurityExpression createSecurityExpression(
      Authentication authentication,
      UserRepository userRepository,
      ExerciseRepository exerciseRepository,
      PermissionEvaluator permissionEvaluator,
      AuthenticationTrustResolver trustResolver,
      RoleHierarchy roleHierarchy) {
    SecurityExpression se =
        new SecurityExpression(authentication, userRepository, exerciseRepository);
    se.setPermissionEvaluator(permissionEvaluator);
    se.setTrustResolver(trustResolver);
    se.setRoleHierarchy(roleHierarchy);
    return se;
  }

  @Override
  public EvaluationContext createEvaluationContext(
      Supplier<Authentication> authentication, MethodInvocation invocation) {
    StandardEvaluationContext context =
        (StandardEvaluationContext) super.createEvaluationContext(authentication, invocation);
    MethodSecurityExpressionOperations delegate =
        (MethodSecurityExpressionOperations) context.getRootObject().getValue();
    assert delegate != null;
    this.securityExpression =
        createSecurityExpression(
            delegate.getAuthentication(),
            userRepository,
            exerciseRepository,
            getPermissionEvaluator(),
            this.trustResolver,
            getRoleHierarchy());
    context.setRootObject(this.securityExpression);
    return context;
  }
}
