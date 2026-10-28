package io.openaev.aop;

import io.openaev.config.SessionHelper;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.User;
import io.openaev.ee.EnterpriseEditionException;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Aspect
@Component
// Innermost of the chain: inner to the audit aspect (LP-1) and, crucially, to both tenant scoping
// aspects (LP-3 / LP-2). The permission check below loads entities to resolve a resource's parent,
// so it must never run before the tenant scope of its own transaction has been written.
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class AccessControlAspect {

  private final PermissionService permissionService;
  private final UserService userService;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final AmbientTenantBridge ambientTenantBridge;

  private final ExpressionParser parser = new SpelExpressionParser();

  /**
   * Authorizes the call, then runs it, in the tenant its {@link TxCtx} argument pins when that
   * tenant is not the ambient one.
   *
   * <p>The caller's capabilities and grants, the {@code tenantFilter} and the tenant stamped on new
   * rows of tables not yet tenant-active all resolve through the ambient {@link TenantContext},
   * which only the {@code /api/tenants/{tenantId}/} route sets, while the {@link TxCtx} also
   * honours the {@code X-Tenant-Ids} header. A request whose scope pins one tenant is therefore
   * authorized and run in that tenant, as on the prefixed route. A scope naming several tenants or
   * none, and a method with no {@link TxCtx} argument, keep the ambient tenant.
   */
  @Around("@annotation(accessControl)")
  public Object authorizeInScopeTenant(ProceedingJoinPoint joinPoint, AccessControl accessControl)
      throws Throwable {
    String tenantId = pinnedTenant(joinPoint.getArgs());
    if (tenantId == null) {
      methodRBACVerification(joinPoint, accessControl);
      return joinPoint.proceed();
    }
    return ambientTenantBridge.callInTenantChecked(
        tenantId,
        () -> {
          methodRBACVerification(joinPoint, accessControl);
          return proceed(joinPoint);
        });
  }

  public void methodRBACVerification(JoinPoint joinPoint, AccessControl accessControl)
      throws AuthenticationException {
    // Enterprise Edition gating runs BEFORE the skipRBAC early return so that endpoints which
    // intentionally bypass RBAC (e.g. AI proxy endpoints scoped by configuration rather than per
    // resource) can still declare `@AccessControl(skipRBAC = true, isEnterpriseEdition = true)`
    // and have the aspect actually enforce the EE license check.
    if (accessControl.isEnterpriseEdition()) {
      if (enterpriseEditionService.isEnterpriseLicenseInactive(
          licenseCacheManager.getEnterpriseEditionInfo())) {
        throw new EnterpriseEditionException("Enterprise Edition license required");
      }
    }

    if (accessControl.skipRBAC()) {
      // If RBAC is disabled, skip the per-resource permission verification (EE gate above still
      // applies when requested).
      return;
    }

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
    Optional<HttpMappingInfo> httpMappingInfo = getHttpMappingInfo(method, paramMap);

    // Create SpEL evaluation context to retrieve the resource ID if it exists
    EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();

    // Add all method parameters to context
    for (int i = 0; i < parameterNames.length; i++) {
      context.setVariable(parameterNames[i], args[i]);
    }

    // Evaluate SpEL expressions to retrieve the resource ID if present
    String resourceId = "";
    if (!accessControl.resourceId().isEmpty()) {
      Expression exp = parser.parseExpression(accessControl.resourceId());
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
        accessControl.requireAdmin()
            ? permissionService.hasUserAdminPrivileges(principal)
            : permissionService.hasPermission(
                principal,
                httpMappingInfo,
                resourceId,
                accessControl.resourceType(),
                accessControl.actionPerformed());

    if (!allowed) {
      log.warn(
          "Access denied for user: {} on resource: {} of type: {} and action: {}",
          principal.getId(),
          resourceId,
          accessControl.resourceType(),
          accessControl.actionPerformed());

      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Access denied for user: " + principal.getEmail()) {};
    }
  }

  /**
   * The single tenant a {@link TxCtx} argument pins, or {@code null} when there is no such
   * argument, it pins several tenants or none, or it pins the ambient tenant.
   */
  private static String pinnedTenant(Object[] args) {
    for (Object arg : args) {
      if (arg instanceof TxCtx.Restricted restricted && restricted.tenantIds().size() == 1) {
        String tenantId = restricted.tenantIds().getFirst();
        return tenantId.equals(TenantContext.getCurrentTenant()) ? null : tenantId;
      }
    }
    return null;
  }

  private static Object proceed(ProceedingJoinPoint joinPoint) throws Exception {
    try {
      return joinPoint.proceed();
    } catch (Exception | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new UndeclaredThrowableException(t);
    }
  }

  public record HttpMappingInfo(
      RequestMethod httpMethod, String[] paths, Map<String, Object> args) {}

  private Optional<HttpMappingInfo> getHttpMappingInfo(Method method, Map<String, Object> args) {
    if (method.isAnnotationPresent(GetMapping.class)) {
      GetMapping ann = method.getAnnotation(GetMapping.class);
      return Optional.of(new HttpMappingInfo(RequestMethod.GET, ann.value(), args));
    } else if (method.isAnnotationPresent(PostMapping.class)) {
      PostMapping ann = method.getAnnotation(PostMapping.class);
      return Optional.of(new HttpMappingInfo(RequestMethod.POST, ann.value(), args));
    } else if (method.isAnnotationPresent(PutMapping.class)) {
      PutMapping ann = method.getAnnotation(PutMapping.class);
      return Optional.of(new HttpMappingInfo(RequestMethod.PUT, ann.value(), args));
    } else if (method.isAnnotationPresent(DeleteMapping.class)) {
      DeleteMapping ann = method.getAnnotation(DeleteMapping.class);
      return Optional.of(new HttpMappingInfo(RequestMethod.DELETE, ann.value(), args));
    } else if (method.isAnnotationPresent(PatchMapping.class)) {
      PatchMapping ann = method.getAnnotation(PatchMapping.class);
      return Optional.of(new HttpMappingInfo(RequestMethod.PATCH, ann.value(), args));
    }
    return Optional.empty();
  }
}
