package io.openaev.config;

import static io.openaev.database.model.User.ROLE_ADMIN;
import static io.openaev.database.model.User.ROLE_USER;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

import io.openaev.annotation.AllowRawJdbc;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.model.User;
import io.openaev.service.LogService;
import io.openaev.service.UserService;
import io.openaev.service.settings.SettingService;
import io.openaev.utils.HttpReqRespUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session registry backed by the PostgreSQL session store (Spring Session JDBC).
 *
 * <p>Because sessions are persisted in the database instead of the servlet container memory, every
 * operation here (refresh, invalidation, concurrent-session enforcement, expiry audit) works across
 * platform restarts and across all sessions regardless of when they were created.
 */
@Component
@Slf4j
@AllowRawJdbc(
    reason =
        "reads/deletes spring_session and spring_session_attributes only: Spring Session"
            + " infrastructure tables with no tenant_id column, never tenant business data")
public class SessionManager {

  private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

  /**
   * Session attribute marker set by the logout handler to distinguish explicit logout from timeout.
   */
  public static final String EXPLICIT_LOGOUT = "EXPLICIT_LOGOUT";

  /**
   * Session attribute storing authentication context captured at login and reused at session expiry
   * time.
   */
  public static final String AUTH_SESSION_CONTEXT = "AUTH_SESSION_CONTEXT";

  private static final String ANONYMOUS_ID = "anonymous";
  private static final String EXPIRY_REASON_TIMEOUT = "inactivity_timeout";
  private static final String EXPIRY_REASON_INVALIDATED = "invalidated";
  private static final String EXPIRY_REASON_CONCURRENT_LIMIT = "concurrent_session_limit";

  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
  private final LogService logService;
  private final SettingService settingService;
  private final JdbcTemplate jdbcTemplate;

  public SessionManager(
      FindByIndexNameSessionRepository<? extends Session> sessionRepository,
      @Lazy LogService logService,
      @Lazy SettingService settingService,
      JdbcTemplate jdbcTemplate) {
    this.sessionRepository = sessionRepository;
    this.logService = logService;
    this.settingService = settingService;
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Marks the current HTTP session as authenticated: captures the client context for expiry audit
   * metadata and indexes the session by user id so it can be found (and killed) later, even after a
   * platform restart.
   */
  public static void markAuthenticatedSession(HttpServletRequest request, String userId) {
    AuthSessionContext context = AuthSessionContext.fromRequest(request);
    request.getSession().setAttribute(AUTH_SESSION_CONTEXT, context);
    request
        .getSession()
        .setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userId);
  }

    // -- SESSION REGISTRY --
  /**
   * Invalidates the current HTTP session and clears the {@link
   * org.springframework.security.core.context.SecurityContextHolder SecurityContextHolder} so no
   * user is left authenticated. Resolves the current request via {@link
   * org.springframework.web.context.request.RequestContextHolder RequestContextHolder} — must be
   * called from a servlet thread.
   */
  public static void invalidateCurrentSession() {
    try {
      SecurityContextHolder.clearContext();
      var attrs = RequestContextHolder.getRequestAttributes();
      if (attrs instanceof ServletRequestAttributes servletAttrs) {
        HttpSession session = servletAttrs.getRequest().getSession(false);
        if (session != null) {
          session.invalidate();
        }
      }
    } catch (Exception e) {
      log.warn("[SESSION] Failed to invalidate current session: {}", e.getMessage(), e);
    }
  }

  // -- SESSION REGISTRY --

  @SuppressWarnings("unchecked")
  private FindByIndexNameSessionRepository<Session> repository() {
    return (FindByIndexNameSessionRepository<Session>) this.sessionRepository;
  }

  private Map<String, Session> getUserSessions(String userId) {
    return repository().findByPrincipalName(userId);
  }

  /**
   * Rewrites the persisted authentication of every live session of the given user, so profile or
   * permission changes take effect without forcing a re-login.
   */
  public void refreshUserSessions(User databaseUser) {
    getUserSessions(databaseUser.getId())
        .values()
        .forEach(
            session -> {
              Object attribute = session.getAttribute(SPRING_SECURITY_CONTEXT_KEY);
              if (!(attribute instanceof SecurityContext securityContext)) {
                return;
              }
              Authentication authentication = securityContext.getAuthentication();
              Authentication newAuthentication =
                  rebuildAuthentication(authentication, databaseUser);
              if (newAuthentication == null) {
                return;
              }
              securityContext.setAuthentication(newAuthentication);
              session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, securityContext);
              repository().save(session);
            });
  }

  private Authentication rebuildAuthentication(Authentication authentication, User databaseUser) {
    if (authentication instanceof OAuth2AuthenticationToken oauth) {
      OAuth2User newPrincipal =
          oauth.getPrincipal() instanceof OpenAEVOidcUser
              ? new OpenAEVOidcUser(databaseUser)
              : new OpenAEVOAuth2User(databaseUser);
      return new OAuth2AuthenticationToken(
          newPrincipal, newPrincipal.getAuthorities(), oauth.getAuthorizedClientRegistrationId());
    }
    if (authentication instanceof Saml2Authentication saml) {
      List<SimpleGrantedAuthority> roles = new ArrayList<>();
      roles.add(new SimpleGrantedAuthority(ROLE_USER));
      if (databaseUser.isAdmin()) {
        roles.add(new SimpleGrantedAuthority(ROLE_ADMIN));
      }
      return new Saml2Authentication(
          new OpenAEVSaml2User(databaseUser, roles), saml.getSaml2Response(), roles);
    }
    if (authentication instanceof PreAuthenticatedAuthenticationToken) {
      return UserService.buildAuthenticationToken(databaseUser);
    }
    return null;
  }

  /** Kills every session of the given user (admin action, user deletion, deactivation). */
  public void invalidateUserSession(String userId) {
    getUserSessions(userId)
        .values()
        .forEach(session -> killSessionWithAudit(session, userId, EXPIRY_REASON_INVALIDATED));
  }

  /**
   * Kills every session of the given user except the provided one (e.g. after a password change,
   * the session that performed the change stays alive).
   */
  public void invalidateOtherUserSessions(String userId, String currentSessionId) {
    getUserSessions(userId).values().stream()
        .filter(session -> !session.getId().equals(currentSessionId))
        .forEach(session -> killSessionWithAudit(session, userId, EXPIRY_REASON_INVALIDATED));
  }

  /** Kills a single session by id. Returns true when the session existed. */
  public boolean invalidateSession(String sessionId) {
    Session session = repository().findById(sessionId);
    if (session == null) {
      return false;
    }
    String userId =
        session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
    killSessionWithAudit(session, userId, EXPIRY_REASON_INVALIDATED);
    return true;
  }

  private void killSessionWithAudit(Session session, String userId, String reason) {
    emitSessionExpiredEvent(
        userId,
        session.getId(),
        session.getCreationTime(),
        session.getLastAccessedTime(),
        reason,
        session.getAttribute(AUTH_SESSION_CONTEXT));
    repository().deleteById(session.getId());
  }

  // -- CONCURRENT SESSIONS LIMIT --

  /**
   * Enforces the {@code platform_session_max_concurrent} platform setting at login time by evicting
   * the oldest sessions of the user (0 = unlimited). The freshly created session (not yet persisted
   * at this point of the request) is always kept.
   *
   * @return the number of evicted sessions
   */
  public int enforceSessionLimit(String userId, String currentSessionId) {
    int maxConcurrent = settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT);
    if (maxConcurrent <= 0) {
      return 0;
    }
    List<Session> otherSessions =
        getUserSessions(userId).values().stream()
            .filter(session -> !session.getId().equals(currentSessionId))
            .sorted(Comparator.comparing(Session::getCreationTime))
            .toList();
    // The current session takes one slot: only maxConcurrent - 1 other sessions may remain.
    int toEvict = otherSessions.size() - (maxConcurrent - 1);
    if (toEvict <= 0) {
      return 0;
    }
    otherSessions.stream()
        .limit(toEvict)
        .forEach(session -> killSessionWithAudit(session, userId, EXPIRY_REASON_CONCURRENT_LIMIT));
    return toEvict;
  }

  // -- SESSION LISTING (admin) --

  /** Lightweight view of a persisted session. */
  public record SessionInfo(
      String sessionId,
      String userId,
      Instant createdAt,
      Instant lastAccessAt,
      Instant expiresAt) {}

  /** Lists every authenticated session currently persisted, most recent first. */
  public List<SessionInfo> findAllSessions() {
    return jdbcTemplate.query(
        "SELECT session_id, principal_name, creation_time, last_access_time, expiry_time"
            + " FROM spring_session WHERE principal_name IS NOT NULL ORDER BY creation_time DESC",
        (rs, rowNum) ->
            new SessionInfo(
                rs.getString("session_id"),
                rs.getString("principal_name"),
                Instant.ofEpochMilli(rs.getLong("creation_time")),
                Instant.ofEpochMilli(rs.getLong("last_access_time")),
                Instant.ofEpochMilli(rs.getLong("expiry_time"))));
  }

  /**
   * Lists every persisted session whose principal belongs to the given set of user ids, most recent
   * first. Used to scope the session registry to a single tenant's members (sessions themselves are
   * platform-global - keyed only by user id - so tenant scoping is an intersection with the
   * tenant's user ids).
   */
  public List<SessionInfo> findSessionsForUsers(Collection<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return List.of();
    }
    Set<String> allowed = new HashSet<>(userIds);
    return findAllSessions().stream()
        .filter(session -> allowed.contains(session.userId()))
        .toList();
  }

  /** Lists the persisted sessions of a single user, most recent first. */
  public List<SessionInfo> findUserSessions(String userId) {
    return getUserSessions(userId).values().stream()
        .map(
            session ->
                new SessionInfo(
                    session.getId(),
                    userId,
                    session.getCreationTime(),
                    session.getLastAccessedTime(),
                    session.getLastAccessedTime().plus(session.getMaxInactiveInterval())))
        .sorted(Comparator.comparing(SessionInfo::createdAt).reversed())
        .toList();
  }

  // -- EXPIRED SESSIONS CLEANUP --

  /**
   * Audit-aware replacement of the built-in Spring Session cleanup job (disabled via {@code
   * spring.session.jdbc.cleanup-cron=-}): emits a {@code session_expired} audit event for every
   * authenticated session that timed out, then deletes the expired rows.
   */
  @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
  @Transactional
  public void cleanUpExpiredSessions() {
    long now = Instant.now().toEpochMilli();
    try {
      List<ExpiredSessionRow> expired =
          jdbcTemplate.query(
              "SELECT s.session_id, s.creation_time, s.last_access_time, s.principal_name,"
                  + " a.attribute_bytes"
                  + " FROM spring_session s"
                  + " LEFT JOIN spring_session_attributes a"
                  + " ON a.session_primary_id = s.primary_id AND a.attribute_name = ?"
                  + " WHERE s.expiry_time < ?",
              (rs, rowNum) ->
                  new ExpiredSessionRow(
                      rs.getString("session_id"),
                      rs.getString("principal_name"),
                      rs.getLong("creation_time"),
                      rs.getLong("last_access_time"),
                      rs.getBytes("attribute_bytes")),
              AUTH_SESSION_CONTEXT,
              now);
      expired.forEach(
          row ->
              emitSessionExpiredEvent(
                  row.principalName(),
                  row.sessionId(),
                  Instant.ofEpochMilli(row.creationTime()),
                  Instant.ofEpochMilli(row.lastAccessTime()),
                  EXPIRY_REASON_TIMEOUT,
                  deserializeAuthContext(row.authContextBytes())));
      jdbcTemplate.update("DELETE FROM spring_session WHERE expiry_time < ?", now);
    } catch (Exception e) {
      log.error("Failed to clean up expired sessions: {}", e.getMessage(), e);
    }
  }

  record ExpiredSessionRow(
      String sessionId,
      String principalName,
      long creationTime,
      long lastAccessTime,
      byte[] authContextBytes) {}

  // -- AUDIT --

  private void emitSessionExpiredEvent(
      String userId,
      String sessionId,
      Instant creationTime,
      Instant lastAccessTime,
      String reason,
      Object authContextAttribute) {
    // Only emit for sessions that went through real authentication.
    if (userId == null || ANONYMOUS_ID.equals(userId)) {
      return;
    }
    AuthSessionContext authContext =
        authContextAttribute instanceof AuthSessionContext ctx ? ctx : null;
    long activeDurationSeconds =
        (lastAccessTime.toEpochMilli() - creationTime.toEpochMilli()) / 1000;
    try {
      logService.logSessionExpiredEvent(
          userId,
          sessionId,
          activeDurationSeconds,
          reason,
          Optional.ofNullable(authContext).map(AuthSessionContext::clientIp).orElse(null),
          Optional.ofNullable(authContext).map(AuthSessionContext::userAgent).orElse(null));
    } catch (Exception e) {
      // Never let audit failures break session lifecycle operations
      log.error("Failed to emit session expired event: {}", e.getMessage(), e);
    }
  }

  private AuthSessionContext deserializeAuthContext(byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    // Resolve classes through the application classloader: with Spring Boot devtools the app
    // runs in the RestartClassLoader and a plain ObjectInputStream would load the record class
    // from the base classloader, failing the instanceof check below.
    try (ObjectInputStream ois =
        new ConfigurableObjectInputStream(
            new ByteArrayInputStream(bytes), getClass().getClassLoader())) {
      Object object = ois.readObject();
      return object instanceof AuthSessionContext ctx ? ctx : null;
    } catch (Exception e) {
      return null;
    }
  }

  public record AuthSessionContext(String clientIp, String userAgent) implements Serializable {

    public static AuthSessionContext fromRequest(HttpServletRequest request) {
      Map<String, String> headers = HttpReqRespUtils.extractHeaders(request);
      String userAgent = HttpReqRespUtils.extractHeader(headers, "User-Agent");
      String clientIp = HttpReqRespUtils.getClientIpAddressFromHeaders(headers);
      if (clientIp == null && request != null) {
        clientIp = request.getRemoteAddr();
      }
      return new AuthSessionContext(clientIp, userAgent);
    }
  }
}
