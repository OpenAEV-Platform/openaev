package io.openaev.utils.helpers;

import static io.openaev.config.SpringSessionConfig.SESSION_COOKIE_NAME;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
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
