package io.openaev.config;

import static io.openaev.config.SessionHelper.ANONYMOUS_USER;
import static io.openaev.config.TenantUriUtils.TENANT_ID_PATH_VARIABLE;

import io.openaev.aop.AccessControl;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.TenantAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Interceptor that automatically extracts the {@code tenantId} path variable from any request
 * matching {@code /api/tenants/{tenantId}/**}, validates the authenticated user belongs to that
 * tenant, and sets it in the {@link TenantContext}.
 *
 * <p>The membership check is skipped for endpoints operating on the tenant resource itself (i.e.
 * annotated with {@code @AccessControl(resourceType = TENANT)}, such as tenant
 * update/delete/reactivate). For those, the {@code tenantId} is the managed target rather than a
 * scope the caller must belong to; authorization is enforced by the RBAC capability check ({@code
 * MANAGE_TENANTS}/{@code DELETE_TENANTS}) in the {@code AccessControlAspect}.
 */
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

  private final TenantMembershipCacheManager tenantMembershipCacheManager;

  @Override
  @SuppressWarnings("unchecked")
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Map<String, String> pathVariables =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (pathVariables != null && pathVariables.containsKey(TENANT_ID_PATH_VARIABLE)) {
      String tenantId = pathVariables.get(TENANT_ID_PATH_VARIABLE);

      if (!targetsTenantResource(handler)) {
        // Validate the authenticated user belongs to this tenant
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
            && authentication.isAuthenticated()
            && !ANONYMOUS_USER.equals(authentication.getPrincipal())) {
          OpenAEVPrincipal principal = (OpenAEVPrincipal) authentication.getPrincipal();
          if (!tenantMembershipCacheManager.existsByUserIdAndTenantId(
              principal.getId(), tenantId)) {
            throw new TenantAccessDeniedException(tenantId);
          }
        }
      }

      TenantContext.setCurrentTenant(tenantId);
    }
    return true;
  }

  /**
   * Returns {@code true} when the target handler operates on the tenant resource itself, i.e. it is
   * annotated with {@code @AccessControl(resourceType = TENANT)}.
   */
  private boolean targetsTenantResource(Object handler) {
    if (handler instanceof HandlerMethod handlerMethod) {
      AccessControl accessControl = handlerMethod.getMethodAnnotation(AccessControl.class);
      return accessControl != null && accessControl.resourceType() == ResourceType.TENANT;
    }
    return false;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    TenantContext.clearCurrentTenant();
  }
}
