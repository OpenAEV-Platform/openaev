package io.openaev.config;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

import io.openaev.database.model.User;
import io.openaev.service.LogService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@Configuration
public class SessionManager {

  /**
   * Session attribute marker set by the logout handler to distinguish explicit logout from timeout.
   */
  public static final String EXPLICIT_LOGOUT = "EXPLICIT_LOGOUT";

  /**
   * Session attribute set during real authentication (login). Only sessions carrying this marker
   * will emit a session_expired audit event. This prevents SSE/polling ephemeral sessions from
   * generating spurious audit noise.
   */
  public static final String AUTHENTICATED_SESSION = "AUTHENTICATED_SESSION";

  private static final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();

  private final LogService logService;

  public SessionManager(@Lazy LogService logService) {
    this.logService = logService;
  }

  @Bean
  public HttpSessionListener httpSessionListener() {
    return new HttpSessionListener() {
      @Override
      public void sessionCreated(HttpSessionEvent hse) {
        sessions.put(hse.getSession().getId(), hse.getSession());
      }

      @Override
      public void sessionDestroyed(HttpSessionEvent hse) {
        HttpSession session = hse.getSession();
        try {
          emitSessionExpiredEvent(session);
        } finally {
          sessions.remove(session.getId());
        }
      }
    };
  }

  private void emitSessionExpiredEvent(HttpSession session) {
    // Only emit for sessions that went through real authentication (marker set in success handler)
    Object authenticatedMarker;
    try {
      authenticatedMarker = session.getAttribute(AUTHENTICATED_SESSION);
    } catch (IllegalStateException e) {
      return;
    }
    if (!Boolean.TRUE.equals(authenticatedMarker)) {
      return;
    }

    // Do not emit if the session was explicitly invalidated via logout
    Object logoutMarker;
    try {
      logoutMarker = session.getAttribute(EXPLICIT_LOGOUT);
    } catch (IllegalStateException e) {
      return;
    }
    if (Boolean.TRUE.equals(logoutMarker)) {
      return;
    }

    // Extract user ID from the session's security context
    String userId = null;
    try {
      Object ctx = session.getAttribute(SPRING_SECURITY_CONTEXT_KEY);
      if (ctx instanceof SecurityContext secCtx) {
        Authentication auth = secCtx.getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OpenAEVPrincipal principal) {
          userId = principal.getId();
        }
      }
    } catch (IllegalStateException e) {
      return;
    }

    if (userId == null || "anonymous".equals(userId)) {
      return;
    }

    long creationTime = session.getCreationTime();
    long lastAccessed = session.getLastAccessedTime();
    long activeDurationSeconds = (lastAccessed - creationTime) / 1000;

    logService.logSessionExpiredEvent(
        userId, session.getId(), activeDurationSeconds, "inactivity_timeout");
  }

  private Optional<SecurityContext> extractSecurityContext(HttpSession httpSession) {
    Object securityContext = httpSession.getAttribute(SPRING_SECURITY_CONTEXT_KEY);
    if (securityContext instanceof SecurityContext secContext) {
      return Optional.of(secContext);
    }
    return Optional.empty();
  }

  private Optional<Authentication> extractAuthentication(HttpSession httpSession) {
    Optional<SecurityContext> securityContext = extractSecurityContext(httpSession);
    if (securityContext.isPresent()) {
      Authentication authentication = securityContext.get().getAuthentication();
      return Optional.of(authentication);
    }
    return Optional.empty();
  }

  private Optional<OpenAEVPrincipal> extractPrincipal(HttpSession httpSession) {
    Optional<Authentication> authentication = extractAuthentication(httpSession);
    if (authentication.isPresent()) {
      Object principal = authentication.get().getPrincipal();
      if (principal instanceof OpenAEVPrincipal user) {
        return Optional.of(user);
      }
    }
    return Optional.empty();
  }

  private Stream<HttpSession> getUserSessions(String userId) {
    return sessions.values().stream()
        .filter(
            httpSession -> {
              try {
                Optional<OpenAEVPrincipal> extractPrincipal = extractPrincipal(httpSession);
                return extractPrincipal.map(user -> user.getId().equals(userId)).orElse(false);
              } catch (IllegalStateException e) {
                return false;
              }
            });
  }

  public void refreshUserSessions(User databaseUser) {
    getUserSessions(databaseUser.getId())
        .forEach(
            httpSession -> {
              Optional<SecurityContext> context = extractSecurityContext(httpSession);
              Optional<Authentication> auth = extractAuthentication(httpSession);
              OpenAEVPrincipal user = extractPrincipal(httpSession).orElseThrow();
              if (context.isPresent() && auth.isPresent()) {
                Authentication authentication = auth.get();
                SecurityContext securityContext = context.get();
                if (authentication instanceof OAuth2AuthenticationToken oauth) {
                  OAuth2User oAuth2User = (OAuth2User) user;
                  Authentication newAuth =
                      new OAuth2AuthenticationToken(
                          oAuth2User,
                          oAuth2User.getAuthorities(),
                          oauth.getAuthorizedClientRegistrationId());
                  securityContext.setAuthentication(newAuth);
                } else if (authentication instanceof PreAuthenticatedAuthenticationToken) {
                  Authentication newAuth =
                      new PreAuthenticatedAuthenticationToken(
                          user, databaseUser.getPassword(), user.getAuthorities());
                  securityContext.setAuthentication(newAuth);
                }
                // TODO ADD SAML2
              }
            });
  }

  public void invalidateUserSession(String userId) {
    getUserSessions(userId).forEach(HttpSession::invalidate);
  }
}
