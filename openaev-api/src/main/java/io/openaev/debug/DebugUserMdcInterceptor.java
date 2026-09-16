package io.openaev.debug;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * Tags a request's log lines with {@code user=...} (the id of the caller, from {@link
 * DebugUserSource}), so it is clear who triggered a given request. Lowest precedence, so it runs
 * after authentication has populated the security context.
 *
 * <p>{@link AsyncHandlerInterceptor} (not just {@code HandlerInterceptor}): on an async dispatch
 * (e.g. a {@code StreamingResponseBody} endpoint) the initial servlet thread exits through {@link
 * #afterConcurrentHandlingStarted} instead of {@code afterCompletion}, and the MDC key must be
 * cleared there too so the pooled thread does not keep the previous caller's {@code user=}.
 */
public class DebugUserMdcInterceptor implements AsyncHandlerInterceptor {

  static final String USER_MDC_KEY = "user";

  private final DebugUserSource userSource;

  public DebugUserMdcInterceptor(DebugUserSource userSource) {
    this.userSource = userSource;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    MDC.put(USER_MDC_KEY, userSource.currentUser());
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    MDC.remove(USER_MDC_KEY);
  }

  @Override
  public void afterConcurrentHandlingStarted(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    MDC.remove(USER_MDC_KEY);
  }
}
