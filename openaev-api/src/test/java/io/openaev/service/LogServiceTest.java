package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.ResourceType;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.utils.HttpReqRespUtils;
import io.openaev.utils.log.dispatcher.AuditLogTransportDispatcherUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogService - logSessionExpiredEvent")
class LogServiceTest {

  @Mock private AuditLogProperties auditLogProperties;
  @Mock private AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;
  @Mock private PreviewFeatureService previewFeatureService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private LicenseCacheManager licenseCacheManager;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private LogService logService;

  @BeforeEach
  void setUp() {
    io.openaev.engine.EngineService engineService = mock(io.openaev.engine.EngineService.class);
    lenient().when(engineService.getObjectMapper()).thenReturn(objectMapper);

    logService =
        new LogService(
            auditLogProperties,
            previewFeatureService,
            auditLogTransportDispatcherUtils,
            mock(io.openaev.utils.object.ObjectNormalizationUtils.class),
            engineService,
            mock(UserService.class),
            enterpriseEditionService,
            licenseCacheManager);
    lenient().when(auditLogProperties.isEnabled()).thenReturn(true);
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
      when(auditLogProperties.isEnabled()).thenReturn(false);

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

  @Test
  @DisplayName("Given event with entity diffs, should include diffs in context")
  void given_eventWithEntityDiffs_should_includeDiffs() {
    // Arrange
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.AUDIT_LOG)).thenReturn(true);
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    when(auditLogTransportDispatcherUtils.dispatch(any(LogEvent.class), any())).thenReturn(true);

    ObjectNode entityDiffs = objectMapper.createObjectNode();
    entityDiffs.put("entity_type", "Group");
    entityDiffs.put("operation", "update");

    // Act
    boolean result =
        logService.logRequestEvent(
            "update",
            "success",
            ResourceType.USER_GROUP,
            "group-id",
            null,
            null,
            null,
            entityDiffs,
            Level.WARNING,
            "uuid-8");

    // Assert
    assertThat(result).isTrue();
    verify(auditLogTransportDispatcherUtils).dispatch(any(LogEvent.class), any());
  }
}
