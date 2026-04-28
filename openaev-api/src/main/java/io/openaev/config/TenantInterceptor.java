package io.openaev.config;

import static io.openaev.config.SessionHelper.ANONYMOUS_USER;
import static io.openaev.config.TenantUriUtils.TENANT_ID_PATH_VARIABLE;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.hibernate.Session;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Interceptor that automatically extracts the {@code tenantId} path variable from any request
 * matching {@code /api/tenants/{tenantId}/**}, validates the authenticated user belongs to that
 * tenant, and sets it in the {@link TenantContext}.
 *
 * <p>Also sets the PostgreSQL session variable {@code app.current_tenant} so that Row-Level
 * Security policies are enforced for <em>all</em> queries in the request, including those outside
 * an explicit {@code @Transactional} block.
 */
@Component
@RequiredArgsConstructor
@Log
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

      // Set the PostgreSQL session variable for RLS enforcement on all query types
      try {
        Session session = entityManager.unwrap(Session.class);
        session.doWork(
            connection ->
                connection
                    .createStatement()
                    .execute("SET app.current_tenant = '" + tenantId.replace("'", "''") + "'"));
      } catch (Exception e) {
        log.warning("Could not set app.current_tenant on connection: " + e.getMessage());
      }
    }
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    TenantContext.clearCurrentTenant();
  }
}
