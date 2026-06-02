package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.config.audit_log.AuditLogProperties;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.utils.HttpReqRespUtils;
import io.openaev.utils.log.dispatcher.AuditLogTransportDispatcherUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogService - logSessionExpiredEvent")
class LogServiceTest {

  @Mock private AuditLogProperties auditLogProperties;
  @Mock private AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;
  @Mock private PreviewFeatureService previewFeatureService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private LicenseCacheManager licenseCacheManager;

  private LogService logService;

  @BeforeEach
  void setUp() {
    logService =
        new LogService(
            auditLogProperties,
            previewFeatureService,
            auditLogTransportDispatcherUtils,
            mock(io.openaev.utils.object.ObjectNormalizationUtils.class),
            mock(io.openaev.engine.EngineService.class),
            mock(UserService.class),
            enterpriseEditionService,
            licenseCacheManager);
    ReflectionTestUtils.setField(logService, "auditLogsEnabled", true);
    lenient().when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
  }

  @Nested
  @DisplayName("logSessionExpiredEvent")
  class LogSessionExpired {

    @Test
    @DisplayName("given_auditEnabled_should_buildAndDispatchEvent")
    void given_auditEnabled_should_buildAndDispatchEvent() {
      // -- PREPARE --
      when(previewFeatureService.isFeatureEnabled(any())).thenReturn(true);
      when(auditLogTransportDispatcherUtils.dispatch(any(LogEvent.class), any())).thenReturn(true);

      // -- EXECUTE --
      boolean result =
          logService.logSessionExpiredEvent(
              "user-123", "session-456", 3600L, "inactivity_timeout", "1.1.1.1", "test");

      // -- VERIFY --
      assertTrue(result);

      ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
      verify(auditLogTransportDispatcherUtils).dispatch(captor.capture(), any());

      LogEvent event = captor.getValue();
      assertEquals("authentication", event.getEventType());
      assertEquals("success", event.getEventStatus());
      assertEquals("session_expired", event.getEventScope());
      assertEquals("user-123", event.getUserId());
      assertNotNull(event.getContextData());
      assertEquals("session-456", event.getContextData().get("session_id"));
      assertEquals("user-123", event.getContextData().get("user_id"));
      assertEquals(3600L, event.getContextData().get("session_active_duration_seconds"));
      assertEquals("inactivity_timeout", event.getContextData().get("expiry_reason"));
      assertEquals(
          "Session expired: active for 3600s, then expired due to inactivity timeout",
          event.getContextData().get("message"));
    }

    @Test
    @DisplayName("given_httpSessionContext_should_setSessionIdInUserMetadata")
    void given_httpSessionContext_should_setSessionIdInUserMetadata() {
      // -- PREPARE --
      when(previewFeatureService.isFeatureEnabled(any())).thenReturn(true);
      when(auditLogTransportDispatcherUtils.dispatch(any(LogEvent.class), any())).thenReturn(true);

      HttpServletRequest request = mock(HttpServletRequest.class);

      // -- EXECUTE --
      boolean result;
      try (MockedStatic<HttpReqRespUtils> mockedHttpReqRespUtils =
          mockStatic(HttpReqRespUtils.class, CALLS_REAL_METHODS)) {
        mockedHttpReqRespUtils.when(HttpReqRespUtils::getCurrentRequest).thenReturn(request);
        result =
            logService.logSessionExpiredEvent(
                "user-123", "session-456", 3600L, "inactivity_timeout", "1.1.1.1", "test");
      }

      // -- VERIFY --
      assertTrue(result);

      ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
      verify(auditLogTransportDispatcherUtils).dispatch(captor.capture(), any());
      LogEvent event = captor.getValue();
      assertNotNull(event.getUserMetadata());
      assertEquals("session-456", event.getUserMetadata().getSessionId());
    }

    @Test
    @DisplayName("given_explicitTenantIpUserAgent_should_setThemOnEvent")
    void given_explicitTenantIpUserAgent_should_setThemOnEvent() {
      // -- PREPARE --
      when(previewFeatureService.isFeatureEnabled(any())).thenReturn(true);
      when(auditLogTransportDispatcherUtils.dispatch(any(LogEvent.class), any())).thenReturn(true);

      // -- EXECUTE --
      boolean result =
          logService.logSessionExpiredEvent(
              "user-123", "session-456", 3600L, "inactivity_timeout", "10.0.0.1", "ua/1.0");

      // -- VERIFY --
      assertTrue(result);

      ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
      verify(auditLogTransportDispatcherUtils).dispatch(captor.capture(), any());
      LogEvent event = captor.getValue();
      assertNotNull(event.getUserMetadata());
      assertEquals("session-456", event.getUserMetadata().getSessionId());
      assertEquals("10.0.0.1", event.getUserMetadata().getIp());
      assertEquals("ua/1.0", event.getUserMetadata().getUserAgent());
    }

    @Test
    @DisplayName("given_auditDisabled_should_returnTrueWithoutDispatching")
    void given_auditDisabled_should_returnTrueWithoutDispatching() {
      // -- PREPARE --
      ReflectionTestUtils.setField(logService, "auditLogsEnabled", false);

      // -- EXECUTE --
      boolean result =
          logService.logSessionExpiredEvent(
              "user-1", "sess-1", 60L, "inactivity_timeout", "1.1.1.1", "test");

      // -- VERIFY --
      assertTrue(result);
      verify(auditLogTransportDispatcherUtils, never()).dispatch(any(LogEvent.class), any());
    }

    @Test
    @DisplayName("given_previewFeatureDisabled_should_returnTrueWithoutDispatching")
    void given_previewFeatureDisabled_should_returnTrueWithoutDispatching() {
      // -- PREPARE --
      when(previewFeatureService.isFeatureEnabled(any())).thenReturn(false);

      // -- EXECUTE --
      boolean result =
          logService.logSessionExpiredEvent(
              "user-1", "sess-1", 60L, "inactivity_timeout", "1.1.1.1", "test");

      // -- VERIFY --
      assertTrue(result);
      verify(auditLogTransportDispatcherUtils, never()).dispatch(any(LogEvent.class), any());
    }

    @Test
    @DisplayName("given_dispatchException_should_returnFalse")
    void given_dispatchException_should_returnFalse() {
      // -- PREPARE --
      when(previewFeatureService.isFeatureEnabled(any())).thenReturn(true);
      when(auditLogTransportDispatcherUtils.dispatch(any(LogEvent.class), any()))
          .thenThrow(new RuntimeException("dispatch failed"));

      // -- EXECUTE --
      boolean result =
          assertDoesNotThrow(
              () ->
                  logService.logSessionExpiredEvent(
                      "user-1", "sess-1", 60L, "inactivity_timeout", "1.1.1.1", "test"));

      // -- VERIFY --
      assertFalse(result);
      verify(auditLogTransportDispatcherUtils).dispatch(any(LogEvent.class), any());
    }
  }
}
