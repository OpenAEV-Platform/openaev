package io.openaev.utils.helpers;

import static io.openaev.config.SpringSessionConfig.SESSION_COOKIE_NAME;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

/**
 * Creates and inspects Spring Session JDBC sessions for integration tests. MockMvc only reaches
 * them through the session cookie: Spring Session ignores a {@code MockHttpSession}.
 */
@Component
public class SessionTestHelper {

  @Autowired private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  /** Persists an anonymous session, as a browser holds before logging in. */
  public String createSession() {
    Session session = repository().createSession();
    repository().save(session);
    return session.getId();
  }

  /**
   * Persists a session already carrying a Spring Security authentication, indexed by principal name
   * so {@code SessionManager} (session registry: invalidation, listing) can find it - as if it had
   * been established, by whatever means, before the test's point of interest (e.g. a rotation or
   * revocation) ran.
   */
  public String createAuthenticatedSession(Authentication authentication, String userId) {
    Session session = repository().createSession();
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
    session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userId);
    repository().save(session);
    return session.getId();
  }

  public boolean exists(String sessionId) {
    return repository().findById(sessionId) != null;
  }

  // DefaultCookieSerializer base64-encodes the session id in the cookie value.
  public Cookie cookieFor(String sessionId) {
    return new Cookie(
        SESSION_COOKIE_NAME,
        Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8)));
  }

  public String sessionIdOf(Cookie sessionCookie) {
    return new String(Base64.getDecoder().decode(sessionCookie.getValue()), StandardCharsets.UTF_8);
  }

  @SuppressWarnings("unchecked")
  private SessionRepository<Session> repository() {
    return (SessionRepository<Session>) sessionRepository;
  }
}
