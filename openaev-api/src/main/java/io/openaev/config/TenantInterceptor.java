package io.openaev.config;

import static io.openaev.config.SessionHelper.ANONYMOUS_USER;
import static io.openaev.config.TenantUriUtils.TENANT_ID_PATH_VARIABLE;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.rest.exception.TenantAccessDeniedException;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Interceptor that automatically extracts the {@code tenantId} path variable from any request
 * matching {@code /api/tenants/{tenantId}/**}, validates the authenticated user belongs to that
 * tenant, sets it in the {@link TenantContext}, and synchronizes the PostgreSQL RLS session
 * variable {@code app.current_tenant} on the current JDBC connection.
 */
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

  private final TenantMembershipCacheManager tenantMembershipCacheManager;
  private final EntityManager entityManager;

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
          throw new TenantAccessDeniedException(tenantId);
        }
      }

      TenantContext.setCurrentTenant(tenantId);

      // Sync RLS session variable on the current JDBC connection so that
      // PostgreSQL Row-Level Security policies use the correct tenant,
      // even if the connection was checked out before this interceptor ran (OSIV).
      syncRlsVariable(tenantId);
    }
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    TenantContext.clearCurrentTenant();
  }

  private void syncRlsVariable(String tenantId) {
    Session session = entityManager.unwrap(Session.class);
    session.doWork(
        connection -> {
          try (var stmt =
              connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
            stmt.setString(1, tenantId);
            stmt.execute();
          }
        });
  }
}
