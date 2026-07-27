package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.ThreadPoolTaskLoggerConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.EventType;
import io.openaev.database.model.ResourceType;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.helper.CryptoHelper;
import io.openaev.utils.HttpReqRespUtils;
import io.openaev.utils.log.dispatcher.AuditLogTransportDispatcherUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
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
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private PreviewFeatureService previewFeatureService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private LogService logService;

  @BeforeEach
  void setUp() {
    io.openaev.engine.EngineService engineService = mock(io.openaev.engine.EngineService.class);
    lenient().when(engineService.getObjectMapper()).thenReturn(objectMapper);

    logService =
        new LogService(
            auditLogProperties,
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
    @DisplayName("given_dispatchException_should_returnFalse")
    void given_dispatchException_should_returnFalse() {
      // -- PREPARE --
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

  @Nested
  @DisplayName("logAuthEvent")
  class LogAuthEvent {

    @Test
    @DisplayName("given_loginRequestContext_should_populateSessionIdInUserMetadata")
    void given_loginRequestContext_should_populateSessionIdInUserMetadata() {
      // -- PREPARE --
      when(auditLogTransportDispatcherUtils.dispatch(any(LogEvent.class), any())).thenReturn(true);

      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.setRequestContextData(
          new ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData(
              null, null, "POST", "/api/login", "session-xyz", null));

      // -- EXECUTE --
      boolean result;
      try {
        result = logService.logAuthEvent("login", "success", "local", null, null, null);
      } finally {
        ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.clear();
      }

      // -- VERIFY --
      assertTrue(result);

      ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
      verify(auditLogTransportDispatcherUtils).dispatch(captor.capture(), any());
      LogEvent event = captor.getValue();
      assertNotNull(event.getUserMetadata());
      assertEquals("session-xyz", event.getUserMetadata().getSessionId());
    }

    @Test
    @DisplayName("given_noActiveSession_should_notSetSessionIdInUserMetadata")
    void given_noActiveSession_should_notSetSessionIdInUserMetadata() {
      // -- PREPARE --
      when(auditLogTransportDispatcherUtils.dispatch(any(LogEvent.class), any())).thenReturn(true);

      // No ThreadRequestContextHolder set — simulates no active HTTP session

      // -- EXECUTE --
      boolean result = logService.logAuthEvent("logout", "success", "local", null, null, null);

      // -- VERIFY --
      assertTrue(result);

      ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
      verify(auditLogTransportDispatcherUtils).dispatch(captor.capture(), any());
      LogEvent event = captor.getValue();
      // UserMetadata may be null or sessionId must be null — no session to correlate
      if (event.getUserMetadata() != null) {
        assertNull(event.getUserMetadata().getSessionId());
      }
    }
  }

  @Test
  @DisplayName("Given event with entity diffs, should include diffs in context")
  void given_eventWithEntityDiffs_should_includeDiffs() {
    // Arrange
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

  @Nested
  @DisplayName("logRequestEvent — redaction of entity_diffs and signature")
  class LogRequestEventRedaction {

    private static final String REDACTED = "*** Redacted ***";

    @BeforeEach
    void enableAudit() {
      when(auditLogTransportDispatcherUtils.dispatch(any(LogEvent.class), any())).thenReturn(true);
    }

    // -- entity_diffs: sensitive field redaction --

    @Test
    @DisplayName("given_entityDiffsWithPasswordField_should_redactValue")
    void given_entityDiffsWithPasswordField_should_redactValue() {
      // Arrange
      ObjectNode entityDiffs = objectMapper.createObjectNode();
      entityDiffs.put("user_password", "plaintext-secret");
      entityDiffs.put("role_name", "admin");

      // Act
      logService.logRequestEvent(
          "update",
          "success",
          ResourceType.USER_GROUP,
          "id-1",
          null,
          null,
          null,
          entityDiffs,
          Level.WARNING,
          "uuid-r1");

      // Assert
      Map<String, Object> diffs = captureEntityDiffs();
      assertThat(diffs)
          .containsEntry("user_password", REDACTED)
          .containsEntry("role_name", "admin");
    }

    @Test
    @DisplayName("given_entityDiffsWithSecretField_should_redactValue")
    void given_entityDiffsWithSecretField_should_redactValue() {
      // Arrange
      ObjectNode entityDiffs = objectMapper.createObjectNode();
      entityDiffs.put("client_secret", "s3cr3t!");

      // Act
      logService.logRequestEvent(
          "update",
          "success",
          ResourceType.USER_GROUP,
          "id-2",
          null,
          null,
          null,
          entityDiffs,
          Level.WARNING,
          "uuid-r2");

      // Assert
      assertThat(captureEntityDiffs()).containsEntry("client_secret", REDACTED);
    }

    @Test
    @DisplayName("given_entityDiffsWithCredentialField_should_redactValue")
    void given_entityDiffsWithCredentialField_should_redactValue() {
      // Arrange
      ObjectNode entityDiffs = objectMapper.createObjectNode();
      entityDiffs.put("api_credential", "cred-xyz");

      // Act
      logService.logRequestEvent(
          "update",
          "success",
          ResourceType.USER_GROUP,
          "id-3",
          null,
          null,
          null,
          entityDiffs,
          Level.WARNING,
          "uuid-r3");

      // Assert
      assertThat(captureEntityDiffs()).containsEntry("api_credential", REDACTED);
    }

    @Test
    @DisplayName("given_entityDiffsWithTokenField_should_hashValue")
    void given_entityDiffsWithTokenField_should_hashValue() {
      // Arrange – token fields are hashed, not blanket-redacted
      ObjectNode entityDiffs = objectMapper.createObjectNode();
      entityDiffs.put("user_token", "abc-secret-token");

      // Act
      logService.logRequestEvent(
          "update",
          "success",
          ResourceType.USER_GROUP,
          "id-4",
          null,
          null,
          null,
          entityDiffs,
          Level.WARNING,
          "uuid-r4");

      // Assert
      assertThat(captureEntityDiffs())
          .containsEntry("user_token", CryptoHelper.hashWithSHA256("abc-secret-token"));
    }

    @Test
    @DisplayName("given_entityDiffsWithApiKeyField_should_hashValue")
    void given_entityDiffsWithApiKeyField_should_hashValue() {
      // Arrange
      ObjectNode entityDiffs = objectMapper.createObjectNode();
      entityDiffs.put("api_key", "key-123-abc");

      // Act
      logService.logRequestEvent(
          "update",
          "success",
          ResourceType.USER_GROUP,
          "id-5",
          null,
          null,
          null,
          entityDiffs,
          Level.WARNING,
          "uuid-r5");

      // Assert
      assertThat(captureEntityDiffs())
          .containsEntry("api_key", CryptoHelper.hashWithSHA256("key-123-abc"));
    }

    // -- entity_diffs: PII removal (USER resource type only) --

    @Test
    @DisplayName("given_entityDiffsWithPIIFields_and_userResourceType_should_removePIIKeys")
    void given_entityDiffsWithPIIFields_and_userResourceType_should_removePIIKeys() {
      // Arrange
      ObjectNode entityDiffs = objectMapper.createObjectNode();
      entityDiffs.put("user_email", "alice@example.com");
      entityDiffs.put("user_firstname", "Alice");
      entityDiffs.put("user_lastname", "Smith");
      entityDiffs.put("role_name", "analyst"); // non-PII — must survive

      // Act
      logService.logRequestEvent(
          "update",
          "success",
          ResourceType.USER,
          "user-1",
          null,
          null,
          null,
          entityDiffs,
          Level.WARNING,
          "uuid-r6");

      // Assert
      Map<String, Object> diffs = captureEntityDiffs();
      assertThat(diffs)
          .doesNotContainKey("user_email")
          .doesNotContainKey("user_firstname")
          .doesNotContainKey("user_lastname")
          .containsEntry("role_name", "analyst");
    }

    @Test
    @DisplayName("given_entityDiffsWithPIIFields_and_nonUserResourceType_should_keepPIIKeys")
    void given_entityDiffsWithPIIFields_and_nonUserResourceType_should_keepPIIKeys() {
      // Arrange – same payload but for a GROUP resource: PII removal must NOT apply
      ObjectNode entityDiffs = objectMapper.createObjectNode();
      entityDiffs.put("user_email", "alice@example.com");

      // Act
      logService.logRequestEvent(
          "update",
          "success",
          ResourceType.USER_GROUP,
          "group-1",
          null,
          null,
          null,
          entityDiffs,
          Level.WARNING,
          "uuid-r7");

      // Assert
      assertThat(captureEntityDiffs()).containsKey("user_email");
    }

    // -- entity_diffs: non-sensitive fields pass through unchanged --

    @Test
    @DisplayName("given_entityDiffsWithNonSensitiveFields_should_passThroughUnchanged")
    void given_entityDiffsWithNonSensitiveFields_should_passThroughUnchanged() {
      // Arrange
      ObjectNode entityDiffs = objectMapper.createObjectNode();
      entityDiffs.put("entity_type", "Role");
      entityDiffs.put("operation", "update");
      entityDiffs.put("role_description", "platform admin");

      // Act
      logService.logRequestEvent(
          "update",
          "success",
          ResourceType.USER_GROUP,
          "id-8",
          null,
          null,
          null,
          entityDiffs,
          Level.WARNING,
          "uuid-r8");

      // Assert
      assertThat(captureEntityDiffs())
          .containsEntry("entity_type", "Role")
          .containsEntry("operation", "update")
          .containsEntry("role_description", "platform admin");
    }

    // -- entity_diffs: nested sensitive fields --

    @Test
    @DisplayName("given_entityDiffsWithNestedPasswordField_should_redactNestedValue")
    void given_entityDiffsWithNestedPasswordField_should_redactNestedValue() {
      // Arrange – diff entry with a nested "before" snapshot that contains a password key
      ObjectNode beforeSnapshot = objectMapper.createObjectNode();
      beforeSnapshot.put("user_password", "old-hash");
      beforeSnapshot.put("user_name", "bob");

      ObjectNode entityDiffs = objectMapper.createObjectNode();
      entityDiffs.set("before", beforeSnapshot);

      // Act
      logService.logRequestEvent(
          "update",
          "success",
          ResourceType.USER_GROUP,
          "id-9",
          null,
          null,
          null,
          entityDiffs,
          Level.WARNING,
          "uuid-r9");

      // Assert
      @SuppressWarnings("unchecked")
      Map<String, Object> before = (Map<String, Object>) captureEntityDiffs().get("before");
      assertThat(before).containsEntry("user_password", REDACTED).containsEntry("user_name", "bob");
    }

    // -- signature: sensitive field redaction --

    @Test
    @DisplayName("given_signatureWithPasswordField_should_redactPasswordValue")
    void given_signatureWithPasswordField_should_redactPasswordValue() {
      // Arrange
      ObjectNode signature = objectMapper.createObjectNode();
      signature.put("user_password", "plaintext");
      signature.put("algo", "sha256");

      // Act
      logService.logRequestEvent(
          "create",
          "success",
          ResourceType.USER_GROUP,
          "id-10",
          null,
          null,
          signature,
          null,
          Level.WARNING,
          "uuid-r10");

      // Assert
      JsonNode sig = captureSignature();
      assertThat(sig.get("user_password").asText()).isEqualTo(REDACTED);
      assertThat(sig.get("algo").asText()).isEqualTo("sha256");
    }

    @Test
    @DisplayName("given_signatureWithTokenField_should_hashTokenValue")
    void given_signatureWithTokenField_should_hashTokenValue() {
      // Arrange
      ObjectNode signature = objectMapper.createObjectNode();
      signature.put("api_token", "tok-xyz-789");

      // Act
      logService.logRequestEvent(
          "create",
          "success",
          ResourceType.USER_GROUP,
          "id-11",
          null,
          null,
          signature,
          null,
          Level.WARNING,
          "uuid-r11");

      // Assert
      assertThat(captureSignature().get("api_token").asText())
          .isEqualTo(CryptoHelper.hashWithSHA256("tok-xyz-789"));
    }

    @Test
    @DisplayName("given_signatureWithSecretField_should_redactSecretValue")
    void given_signatureWithSecretField_should_redactSecretValue() {
      // Arrange
      ObjectNode signature = objectMapper.createObjectNode();
      signature.put("client_secret", "very-secret");

      // Act
      logService.logRequestEvent(
          "create",
          "success",
          ResourceType.USER_GROUP,
          "id-12",
          null,
          null,
          signature,
          null,
          Level.WARNING,
          "uuid-r12");

      // Assert
      assertThat(captureSignature().get("client_secret").asText()).isEqualTo(REDACTED);
    }

    @Test
    @DisplayName("given_nullSignature_should_notSetSignatureOnRequestMetadata")
    void given_nullSignature_should_notSetSignatureOnRequestMetadata() {
      // Act – no signatureNode passed
      logService.logRequestEvent(
          "create",
          "success",
          ResourceType.USER_GROUP,
          "id-13",
          null,
          null,
          null,
          null,
          Level.WARNING,
          "uuid-r13");

      // Assert
      assertThat(captureEvent().getRequestMetadata().getSignature()).isNull();
    }

    @Test
    @DisplayName("given_signatureWithPIIFields_and_userResourceType_should_removePIIKeys")
    void given_signatureWithPIIFields_and_userResourceType_should_removePIIKeys() {
      // Arrange
      ObjectNode signature = objectMapper.createObjectNode();
      signature.put("user_email", "bob@example.com");
      signature.put("user_phone", "+1-555-0100");
      signature.put("method_name", "updateProfile");

      // Act
      logService.logRequestEvent(
          "update",
          "success",
          ResourceType.USER,
          "user-2",
          null,
          null,
          signature,
          null,
          Level.WARNING,
          "uuid-r14");

      // Assert
      JsonNode sig = captureSignature();
      assertThat(sig.has("user_email")).isFalse();
      assertThat(sig.has("user_phone")).isFalse();
      assertThat(sig.get("method_name").asText()).isEqualTo("updateProfile");
    }

    // -- helpers --

    private LogEvent captureEvent() {
      ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
      verify(auditLogTransportDispatcherUtils, atLeastOnce()).dispatch(captor.capture(), any());
      return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureEntityDiffs() {
      return (Map<String, Object>) captureEvent().getContextData().get("entity_diffs");
    }

    private JsonNode captureSignature() {
      return captureEvent().getRequestMetadata().getSignature();
    }
  }

  @Nested
  @DisplayName("logGenericEvent")
  class LogGenericEvent {

    @Test
    @DisplayName("given_systemOriginEvent_should_nullifyUserMetadata")
    void given_systemOriginEvent_should_nullifyUserMetadata() {
      // Arrange
      when(previewFeatureService.isFeatureEnabled(any())).thenReturn(true);
      when(auditLogTransportDispatcherUtils.dispatch(any(LogEvent.class), any())).thenReturn(true);

      AuditEvent event =
          AuditEvent.builder()
              .eventType(EventType.EXECUTION)
              .eventScope(AuditEventScope.INJECT_STATUS_TRANSITION)
              .eventStatus(EventStatus.SUCCESS)
              .resourceType(ResourceType.INJECT)
              .resourceId("inject-1")
              .message("Inject transitioned from PENDING to EXECUTED")
              .contextData(
                  Map.of(
                      "inject_id", "inject-1",
                      "previous_status", "PENDING",
                      "new_status", "EXECUTED"))
              .origin(AuditEventOrigin.SCHEDULED)
              .build();

      // Act
      boolean result = logService.logGenericEvent(event, Level.WARNING, "uuid-gen-1");

      // Assert
      assertTrue(result);

      ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
      verify(auditLogTransportDispatcherUtils).dispatch(captor.capture(), any());

      LogEvent logEvent = captor.getValue();
      assertThat(logEvent.getEventType()).isEqualTo("execution");
      assertThat(logEvent.getEventScope()).isEqualTo("inject_status_transition");
      assertThat(logEvent.getEventStatus()).isEqualTo("success");
      assertNull(logEvent.getUserId());
      assertNull(logEvent.getUserMetadata());
      assertThat(logEvent.getContextData())
          .containsEntry("message", "Inject transitioned from PENDING to EXECUTED");
      assertThat(logEvent.getContextData()).containsEntry("previous_status", "PENDING");
      assertThat(logEvent.getContextData()).containsEntry("resource_id", "inject-1");
    }

    @Test
    @DisplayName("given_dispatchThrowsException_should_returnFalse")
    void given_dispatchThrowsException_should_returnFalse() {
      // Arrange
      when(previewFeatureService.isFeatureEnabled(any())).thenReturn(true);
      when(auditLogTransportDispatcherUtils.dispatch(any(LogEvent.class), any()))
          .thenThrow(new RuntimeException("transport failure"));

      AuditEvent event =
          AuditEvent.builder()
              .eventType(EventType.SYSTEM)
              .eventScope(AuditEventScope.JOB_EXECUTION)
              .eventStatus(EventStatus.ERROR)
              .message("Job failed")
              .origin(AuditEventOrigin.SCHEDULED)
              .build();

      // Act
      boolean result =
          assertDoesNotThrow(() -> logService.logGenericEvent(event, Level.WARNING, "uuid-gen-2"));

      // Assert
      assertFalse(result);
    }

    @Test
    @DisplayName("given_auditDisabled_should_returnTrueWithoutDispatching")
    void given_auditDisabled_should_returnTrueWithoutDispatching() {
      // Arrange
      when(auditLogProperties.isEnabled()).thenReturn(false);

      AuditEvent event =
          AuditEvent.builder()
              .eventType(EventType.EXECUTION)
              .eventScope(AuditEventScope.SCHEDULED_LAUNCH)
              .eventStatus(EventStatus.SUCCESS)
              .message("Simulation started")
              .origin(AuditEventOrigin.SCHEDULED)
              .build();

      // Act
      boolean result = logService.logGenericEvent(event, Level.WARNING, "uuid-gen-3");

      // Assert
      assertTrue(result);
      verify(auditLogTransportDispatcherUtils, never()).dispatch(any(LogEvent.class), any());
    }

    @Test
    @DisplayName("given_dispatchReturnsFalse_should_returnFalse")
    void given_dispatchReturnsFalse_should_returnFalse() {
      // Arrange
      when(previewFeatureService.isFeatureEnabled(any())).thenReturn(true);
      when(auditLogTransportDispatcherUtils.dispatch(any(LogEvent.class), any())).thenReturn(false);

      AuditEvent event =
          AuditEvent.builder()
              .eventType(EventType.SYSTEM)
              .eventScope(AuditEventScope.RETENTION_PURGE)
              .eventStatus(EventStatus.SUCCESS)
              .message("Purged 1000 records")
              .origin(AuditEventOrigin.SCHEDULED)
              .build();

      // Act
      boolean result = logService.logGenericEvent(event, Level.WARNING, "uuid-gen-4");

      // Assert
      assertFalse(result);
      verify(auditLogTransportDispatcherUtils).dispatch(any(LogEvent.class), any());
    }
  }
}
