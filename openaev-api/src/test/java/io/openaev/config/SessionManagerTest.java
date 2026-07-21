package io.openaev.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.database.model.SettingKeys;
import io.openaev.service.LogService;
import io.openaev.service.settings.SettingService;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionManager - persistent session registry")
class SessionManagerTest {

  @Mock private LogService logService;
  @Mock private SettingService settingService;
  @Mock private JdbcTemplate jdbcTemplate;

  @Mock private FindByIndexNameSessionRepository<Session> sessionRepository;

  private SessionManager sessionManager;

  @BeforeEach
  void setUp() {
    sessionManager =
        new SessionManager(sessionRepository, logService, settingService, jdbcTemplate);
  }

  private MapSession session(String id, Instant createdAt) {
    MapSession session = new MapSession(id);
    session.setCreationTime(createdAt);
    session.setLastAccessedTime(createdAt.plusSeconds(60));
    return session;
  }

  private static byte[] serialize(Object object) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
      oos.writeObject(object);
    }
    return bytes.toByteArray();
  }

  @Nested
  @DisplayName("cleanUpExpiredSessions")
  class CleanUpExpiredSessions {

    @Test
    @DisplayName("given_expiredAuthenticatedSession_should_emitAuditEventAndDelete")
    void given_expiredAuthenticatedSession_should_emitAuditEventAndDelete() throws Exception {
      byte[] authContext = serialize(new SessionManager.AuthSessionContext("10.0.0.1", "ua/1.0"));
      SessionManager.ExpiredSessionRow row =
          new SessionManager.ExpiredSessionRow(
              "session-abc", "user-456", 1_000L, 61_000L, authContext);
      when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
          .thenReturn(List.of(row));

      sessionManager.cleanUpExpiredSessions();

      verify(logService)
          .logSessionExpiredEvent(
              eq("user-456"),
              eq("session-abc"),
              eq(60L),
              eq("inactivity_timeout"),
              eq("10.0.0.1"),
              eq("ua/1.0"));
      verify(jdbcTemplate).update(startsWith("DELETE FROM spring_session"), anyLong());
    }

    @Test
    @DisplayName("given_expiredAnonymousOrUnauthenticatedSessions_should_notEmitEvent")
    void given_expiredAnonymousOrUnauthenticatedSessions_should_notEmitEvent() {
      SessionManager.ExpiredSessionRow anonymous =
          new SessionManager.ExpiredSessionRow("s1", "anonymous", 1_000L, 61_000L, null);
      SessionManager.ExpiredSessionRow unauthenticated =
          new SessionManager.ExpiredSessionRow("s2", null, 1_000L, 61_000L, null);
      when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
          .thenReturn(List.of(anonymous, unauthenticated));

      sessionManager.cleanUpExpiredSessions();

      verify(logService, never())
          .logSessionExpiredEvent(any(), any(), anyLong(), any(), any(), any());
      verify(jdbcTemplate).update(startsWith("DELETE FROM spring_session"), anyLong());
    }

    @Test
    @DisplayName("given_missingAuthContextAttribute_should_emitEventWithoutClientMetadata")
    void given_missingAuthContextAttribute_should_emitEventWithoutClientMetadata() {
      SessionManager.ExpiredSessionRow row =
          new SessionManager.ExpiredSessionRow("session-abc", "user-456", 1_000L, 61_000L, null);
      when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
          .thenReturn(List.of(row));

      sessionManager.cleanUpExpiredSessions();

      verify(logService)
          .logSessionExpiredEvent(
              eq("user-456"),
              eq("session-abc"),
              eq(60L),
              eq("inactivity_timeout"),
              eq(null),
              eq(null));
    }
  }

  @Nested
  @DisplayName("invalidateUserSession")
  class InvalidateUserSession {

    @Test
    @DisplayName("given_userWithSessions_should_deleteAllAndAudit")
    void given_userWithSessions_should_deleteAllAndAudit() {
      MapSession first = session("s1", Instant.ofEpochMilli(1_000));
      MapSession second = session("s2", Instant.ofEpochMilli(2_000));
      when(sessionRepository.findByPrincipalName("user-456"))
          .thenReturn(Map.of("s1", first, "s2", second));

      sessionManager.invalidateUserSession("user-456");

      verify(sessionRepository).deleteById("s1");
      verify(sessionRepository).deleteById("s2");
      verify(logService, times(2))
          .logSessionExpiredEvent(
              eq("user-456"), any(), anyLong(), eq("invalidated"), any(), any());
    }
  }

  @Nested
  @DisplayName("enforceSessionLimit")
  class EnforceSessionLimit {

    @Test
    @DisplayName("given_noLimitConfigured_should_notEvict")
    void given_noLimitConfigured_should_notEvict() {
      when(settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT)).thenReturn(0);

      int evicted = sessionManager.enforceSessionLimit("user-456", "current");

      assertEquals(0, evicted);
      verifyNoInteractions(sessionRepository);
    }

    @Test
    @DisplayName("given_sessionsBelowLimit_should_notEvict")
    void given_sessionsBelowLimit_should_notEvict() {
      when(settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT)).thenReturn(3);
      when(sessionRepository.findByPrincipalName("user-456"))
          .thenReturn(Map.of("s1", session("s1", Instant.ofEpochMilli(1_000))));

      int evicted = sessionManager.enforceSessionLimit("user-456", "current");

      assertEquals(0, evicted);
      verify(sessionRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("given_sessionsAtLimit_should_evictOldestFirst")
    void given_sessionsAtLimit_should_evictOldestFirst() {
      when(settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT)).thenReturn(2);
      MapSession oldest = session("oldest", Instant.ofEpochMilli(1_000));
      MapSession newer = session("newer", Instant.ofEpochMilli(5_000));
      when(sessionRepository.findByPrincipalName("user-456"))
          .thenReturn(Map.of("oldest", oldest, "newer", newer));

      int evicted = sessionManager.enforceSessionLimit("user-456", "current");

      assertEquals(1, evicted);
      verify(sessionRepository).deleteById("oldest");
      verify(sessionRepository, never()).deleteById("newer");
      verify(logService)
          .logSessionExpiredEvent(
              eq("user-456"),
              eq("oldest"),
              anyLong(),
              eq("concurrent_session_limit"),
              any(),
              any());
    }

    @Test
    @DisplayName("given_currentSessionAmongExisting_should_ignoreIt")
    void given_currentSessionAmongExisting_should_ignoreIt() {
      when(settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT)).thenReturn(2);
      MapSession current = session("current", Instant.ofEpochMilli(1_000));
      MapSession other = session("other", Instant.ofEpochMilli(2_000));
      when(sessionRepository.findByPrincipalName("user-456"))
          .thenReturn(Map.of("current", current, "other", other));

      int evicted = sessionManager.enforceSessionLimit("user-456", "current");

      assertEquals(0, evicted);
      verify(sessionRepository, never()).deleteById(any());
    }
  }
}
