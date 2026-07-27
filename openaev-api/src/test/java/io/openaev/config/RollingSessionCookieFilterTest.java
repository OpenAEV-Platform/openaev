package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Regression tests for the rolling session cookie (OpenCTI `rolling: true` equivalent): the
 * browser-side cookie Max-Age must slide with user activity, otherwise users navigating all day are
 * kicked out when the cookie written at login reaches its absolute Max-Age, even though the
 * server-side session is still alive.
 */
@DisplayName("Rolling session cookie filter")
class RollingSessionCookieFilterTest {

  private static final String SESSION_ID = "5cbb35f6-a715-4644-a5e2-4c33bd45d217";

  private RollingSessionCookieFilter buildFilter(boolean rollingEnabled) {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(SpringSessionConfig.SESSION_COOKIE_NAME);
    serializer.setCookiePath("/");
    serializer.setCookieMaxAge(86400);
    return new RollingSessionCookieFilter(serializer, rollingEnabled);
  }

  private MockHttpServletResponse run(
      RollingSessionCookieFilter filter, MockHttpServletRequest request)
      throws ServletException, IOException {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  @Test
  @DisplayName("given a valid unchanged session should re-issue the cookie with a fresh Max-Age")
  void given_validUnchangedSession_should_rollCookie() throws Exception {
    // Arrange - the request presents a session id that is still the current session id.
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestedSessionId(SESSION_ID);
    request.setSession(new MockHttpSession(null, SESSION_ID));

    // Act
    MockHttpServletResponse response = run(buildFilter(true), request);

    // Assert - Set-Cookie re-sent: browser expiration slides with activity.
    String setCookie = response.getHeader("Set-Cookie");
    assertNotNull(setCookie, "the session cookie must be re-issued on an active session");
    assertTrue(setCookie.startsWith(SpringSessionConfig.SESSION_COOKIE_NAME + "="), setCookie);
    assertTrue(setCookie.contains("Max-Age=86400"), setCookie);
  }

  @Test
  @DisplayName("given no session cookie on the request should not write anything")
  void given_noRequestedSession_should_notWriteCookie() throws Exception {
    // Arrange - anonymous request (login page, health checks...).
    MockHttpServletRequest request = new MockHttpServletRequest();

    // Act
    MockHttpServletResponse response = run(buildFilter(true), request);

    // Assert - Spring Session's own resolver owns cookie creation.
    assertNull(response.getHeader("Set-Cookie"));
  }

  @Test
  @DisplayName("given a session invalidated during the request (logout) should not resurrect it")
  void given_invalidatedSession_should_notWriteCookie() throws Exception {
    // Arrange - a session id was presented but no session exists anymore at response time.
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestedSessionId(SESSION_ID);

    // Act
    MockHttpServletResponse response = run(buildFilter(true), request);

    // Assert - the logout deletion cookie must not be overridden by a rolled cookie.
    assertNull(response.getHeader("Set-Cookie"));
  }

  @Test
  @DisplayName("given a session id changed during the request (login) should not write a stale id")
  void given_changedSessionId_should_notWriteCookie() throws Exception {
    // Arrange - session fixation protection rotated the id: the resolver writes the new cookie.
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestedSessionId(SESSION_ID);
    request.setSession(new MockHttpSession(null, "rotated-session-id"));

    // Act
    MockHttpServletResponse response = run(buildFilter(true), request);

    // Assert
    assertNull(response.getHeader("Set-Cookie"));
  }

  @Test
  @DisplayName("given browser-session cookie mode should be a no-op (no Max-Age to slide)")
  void given_rollingDisabled_should_notWriteCookie() throws Exception {
    // Arrange
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestedSessionId(SESSION_ID);
    request.setSession(new MockHttpSession(null, SESSION_ID));

    // Act
    MockHttpServletResponse response = run(buildFilter(false), request);

    // Assert
    assertNull(response.getHeader("Set-Cookie"));
  }

  @Test
  @DisplayName("given an already committed response (streaming) should skip silently")
  void given_committedResponse_should_notWriteCookie() throws Exception {
    // Arrange
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestedSessionId(SESSION_ID);
    request.setSession(new MockHttpSession(null, SESSION_ID));
    RollingSessionCookieFilter filter = buildFilter(true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setCommitted(true);

    // Act
    filter.doFilter(request, response, new MockFilterChain());

    // Assert
    assertNull(response.getHeader("Set-Cookie"));
  }
}
