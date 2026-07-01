package io.openaev.debug;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Tags a request's log lines with {@code tenant=...} (from {@link DebugTenantSource}). Lowest
 * precedence, so it runs after the platform tenant interceptor and the handler mapping.
 */
public class DebugTenantMdcInterceptor implements HandlerInterceptor {

  static final String TENANT_MDC_KEY = "tenant";

  private final DebugTenantSource tenantSource;

  public DebugTenantMdcInterceptor(DebugTenantSource tenantSource) {
    this.tenantSource = tenantSource;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    MDC.put(TENANT_MDC_KEY, tenantSource.currentTenant(request));
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    MDC.remove(TENANT_MDC_KEY);
  }
}
