package io.openaev.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-issues the session cookie on every request carrying a valid session so its {@code Max-Age}
 * slides with user activity - the equivalent of express-session's {@code rolling: true} used by
 * OpenCTI.
 *
 * <p>Without this, the persistent session cookie is only written once at session creation with
 * {@code Max-Age = session timeout}: the browser drops it after that absolute delay even though the
 * server-side session (whose idle timeout DOES slide on every request) is still alive, kicking out
 * users who were navigating all day. With this filter, both the server-side expiration and the
 * browser-side cookie expiration slide together: a user active within the timeout window is never
 * logged out.
 *
 * <p>The cookie is only rewritten when the request presented a session id that is still the id of
 * the current session at response time. In every other case Spring Session's own resolver writes
 * the authoritative cookie and a second {@code Set-Cookie} would fight it: a new session (login), a
 * changed session id (session fixation protection), or an invalidated session (logout, where the
 * resolver writes the cookie deletion).
 */
public class RollingSessionCookieFilter extends OncePerRequestFilter {

  private final CookieSerializer cookieSerializer;

  /**
   * In browser-session cookie mode ({@code openaev.session-cookie=true}) the cookie has no {@code
   * Max-Age}, so there is nothing to slide: the filter is a no-op.
   */
  private final boolean rollingEnabled;

  public RollingSessionCookieFilter(CookieSerializer cookieSerializer, boolean rollingEnabled) {
    this.cookieSerializer = cookieSerializer;
    this.rollingEnabled = rollingEnabled;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // Captured before the chain: the wrapped Spring Session request resolves it from the cookie.
    String requestedSessionId = request.getRequestedSessionId();
    try {
      filterChain.doFilter(request, response);
    } finally {
      rollSessionCookie(request, response, requestedSessionId);
    }
  }

  private void rollSessionCookie(
      HttpServletRequest request, HttpServletResponse response, String requestedSessionId) {
    if (!rollingEnabled || requestedSessionId == null || response.isCommitted()) {
      return;
    }
    HttpSession session = request.getSession(false);
    // Only slide an existing, unchanged session: if the session was created, its id changed or it
    // was invalidated during the request, Spring Session's resolver writes the cookie itself.
    if (session == null || !session.getId().equals(requestedSessionId)) {
      return;
    }
    cookieSerializer.writeCookieValue(
        new CookieSerializer.CookieValue(request, response, session.getId()));
  }
}
