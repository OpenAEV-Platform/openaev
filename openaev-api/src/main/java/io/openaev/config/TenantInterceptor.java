package io.openaev.config;

import static io.openaev.config.TenantUriUtils.TENANT_ID_PATH_VARIABLE;

import io.openaev.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Interceptor that automatically extracts the {@code tenantId} path variable from any request
 * matching {@code /api/tenants/{tenantId}/**} and sets it in the {@link TenantContext}.
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

  @Override
  @SuppressWarnings("unchecked")
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Map<String, String> pathVariables =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (pathVariables != null && pathVariables.containsKey(TENANT_ID_PATH_VARIABLE)) {
      TenantContext.setCurrentTenant(pathVariables.get(TENANT_ID_PATH_VARIABLE));
    }
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    TenantContext.clearCurrentTenant();
  }
}
