package io.openaev.debug;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * Tags a request's log lines with {@code tenant=...} (from {@link DebugTenantSource}). Lowest
 * precedence, so it runs after the platform tenant interceptor and the handler mapping.
 *
 * <p>{@link AsyncHandlerInterceptor} (not just {@code HandlerInterceptor}): on an async dispatch
 * (e.g. a {@code StreamingResponseBody} endpoint) the initial servlet thread exits through {@link
 * #afterConcurrentHandlingStarted} instead of {@code afterCompletion}, and the MDC key must be
 * cleared there too so the pooled thread does not keep the previous request's {@code tenant=}.
 */
public class DebugTenantMdcInterceptor implements AsyncHandlerInterceptor {

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

  @Override
  public void afterConcurrentHandlingStarted(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    MDC.remove(TENANT_MDC_KEY);
  }
}
