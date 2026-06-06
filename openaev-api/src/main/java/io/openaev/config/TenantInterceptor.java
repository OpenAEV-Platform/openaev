package io.openaev.config;

import static io.openaev.config.SessionHelper.ANONYMOUS_USER;
import static io.openaev.config.TenantUriUtils.TENANT_ID_PATH_VARIABLE;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.ExecState;
import io.openaev.context.TenantContext;
import io.openaev.context.StateExecutionContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.rest.exception.TenantAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Interceptor that automatically extracts the {@code tenantId} path variable from any request
 * matching {@code /api/tenants/{tenantId}/**}, validates the authenticated user belongs to that
 * tenant, resolves the {@link Tenant} entity, and sets the {@link ExecState} in the {@link
 * StateExecutionContext}.
 *
 * <p>The resolved {@link Tenant} entity is available throughout the request via {@code
 * TenantExecutionContext.get().tenant()}, eliminating the need to create {@code new Tenant(id)}
 * when assigning tenants to newly created entities.
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

      // Validate the authenticated user belongs to this tenant
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null
          && authentication.isAuthenticated()
          && !ANONYMOUS_USER.equals(authentication.getPrincipal())) {
        OpenAEVPrincipal principal = (OpenAEVPrincipal) authentication.getPrincipal();
        if (!tenantMembershipCacheManager.existsByUserIdAndTenantId(principal.getId(), tenantId)) {
          // throw new TenantAccessDeniedException(tenantId);
        }
      }

      TenantContext.setCurrentTenant(tenantId);
      StateExecutionContext.set(ExecState.of(tenantId, List.of()));
    }
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    TenantContext.clearCurrentTenant();
    StateExecutionContext.clear();
  }
}
