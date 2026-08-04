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
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
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
 *
 * <p>{@link AsyncHandlerInterceptor} (not just {@code HandlerInterceptor}): on an async dispatch
 * (e.g. a {@code StreamingResponseBody} endpoint) the initial servlet thread exits through {@link
 * #afterConcurrentHandlingStarted} instead of {@code afterCompletion}, and the {@link
 * TenantContext} thread-local must be cleared there too so the pooled thread does not carry the
 * previous request's tenant into an unrelated request.
 */
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements AsyncHandlerInterceptor {

  private final TenantMembershipCacheManager tenantMembershipCacheManager;
  private final TenantUriUtils tenantUriUtils;

  @Override
  @SuppressWarnings("unchecked")
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    tenantUriUtils.getTenantIdFromRequestUrl(request).ifPresent(tenantId -> {
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
    });
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

  @Override
  public void afterConcurrentHandlingStarted(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    TenantContext.clearCurrentTenant();
  }
}
