package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.EventType;
import io.openaev.database.model.ResourceType;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.engine.EngineService;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.utils.SystemLoadGuardUtils;
import io.openaev.utils.log.dispatcher.AuditLogTransportDispatcherUtils;
import io.openaev.utils.object.ObjectNormalizationPolicy;
import io.openaev.utils.object.ObjectNormalizationUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Regression tests for the size capping of the audit {@code signature} node.
 *
 * <p>{@code ObjectNormalizationUtils#normalize} is what enforces {@code maxEventSizeBytes}. It used
 * to be applied only to {@code input} and {@code output}, never to {@code signature}. Combined with
 * the aspect failing to detect {@code @RequestPart} payloads, a large DTO reaching the signature
 * node was logged uncapped and exhausted the heap (Nuclei injector registration).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LogService — audit signature size capping")
class LogServiceSignatureNormalizationTest {

  private static final int MAX_EVENT_SIZE_BYTES = 512;
  private static final int MAX_STRING_BYTES = 64;
  private static final String TRUNCATED_SUFFIX = "...[TRUNCATED]";

  /** Stands in for a Nuclei {@code contract_content} blob. */
  private static final String HUGE_CONTRACT_CONTENT = "N".repeat(20_000);

  @Mock private AuditLogProperties auditLogProperties;
  @Mock private AuditLogTransportDispatcherUtils dispatcher;
  @Mock private EngineService engineService;
  @Mock private UserService userService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private SystemLoadGuardUtils systemLoadGuardUtils;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private LogService logService;
  private ArgumentCaptor<LogEvent> logEventCaptor;

  @BeforeEach
  void setup() {
    ObjectNormalizationPolicy policy = new ObjectNormalizationPolicy();
    ReflectionTestUtils.setField(policy, "maxEventSizeBytes", MAX_EVENT_SIZE_BYTES);
    ReflectionTestUtils.setField(policy, "maxStringBytes", MAX_STRING_BYTES);
    ReflectionTestUtils.setField(policy, "truncationPreviewBytes", 128);
    ReflectionTestUtils.setField(policy, "skipOnHighLoad", true);
    ReflectionTestUtils.setField(policy, "skipAllNormalization", false);
    ReflectionTestUtils.setField(policy, "maxProcessCpuLoad", 0.90d);
    ReflectionTestUtils.setField(policy, "maxHeapUsageRatio", 0.90d);

    // Never take the "system under load" shortcut — we want the full normalization path.
    lenient().when(systemLoadGuardUtils.isHeapUsageHigh(anyDouble())).thenReturn(false);
    lenient().when(systemLoadGuardUtils.isProcessCpuLoadHigh(anyDouble())).thenReturn(false);

    ObjectNormalizationUtils normalizationUtils =
        new ObjectNormalizationUtils(objectMapper, systemLoadGuardUtils, policy);

    logService =
        new LogService(
            auditLogProperties,
            dispatcher,
            normalizationUtils,
            engineService,
            userService,
            enterpriseEditionService,
            licenseCacheManager);

    when(auditLogProperties.isEnabled()).thenReturn(true);
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    when(engineService.getObjectMapper()).thenReturn(objectMapper);
    when(dispatcher.dispatch(any(LogEvent.class), any())).thenReturn(true);

    logEventCaptor = ArgumentCaptor.forClass(LogEvent.class);
  }

  /** Builds a signature node shaped like the one produced by the audit aspect. */
  private JsonNode oversizedSignature() {
    ObjectNode signature = objectMapper.createObjectNode();
    signature.put("method", "io.openaev.rest.injector.InjectorApi.registerInjector");
    ObjectNode params = objectMapper.createObjectNode();
    params.put("input", HUGE_CONTRACT_CONTENT);
    signature.set("parameters", params);
    return signature;
  }

  private JsonNode dispatchedSignature() {
    verify(dispatcher).dispatch(logEventCaptor.capture(), any());
    return logEventCaptor.getValue().getRequestMetadata().getSignature();
  }

  @Nested
  @DisplayName("logRequestEvent")
  class LogRequestEventSignature {

    @Test
    @DisplayName("an oversized signature is capped instead of being emitted in full")
    void given_oversizedSignature_should_capEmittedSignature() {
      // Arrange
      JsonNode signature = oversizedSignature();

      // Act
      logService.logRequestEvent(
          "create",
          "success",
          ResourceType.INJECTOR,
          UUID.randomUUID().toString(),
          null,
          null,
          signature,
          null,
          Level.INFO,
          UUID.randomUUID().toString());

      // Assert — the full blob never reaches the transport
      String emitted = dispatchedSignature().toString();
      assertThat(emitted).doesNotContain(HUGE_CONTRACT_CONTENT);
      assertThat(emitted.length()).isLessThan(HUGE_CONTRACT_CONTENT.length());
    }

    @Test
    @DisplayName("a small signature is left intact")
    void given_smallSignature_should_keepMethodName() {
      // Arrange
      ObjectNode signature = objectMapper.createObjectNode();
      signature.put("method", "io.openaev.rest.team.TeamApi.updateTeam");

      // Act
      logService.logRequestEvent(
          "update",
          "success",
          ResourceType.TEAM,
          UUID.randomUUID().toString(),
          null,
          null,
          signature,
          null,
          Level.INFO,
          UUID.randomUUID().toString());

      // Assert
      assertThat(dispatchedSignature().path("method").asText())
          .isEqualTo("io.openaev.rest.team.TeamApi.updateTeam");
    }

    @Test
    @DisplayName("sensitive fields in the signature are still redacted after normalization")
    void given_sensitiveSignatureFields_should_stillRedact() {
      // Arrange
      ObjectNode params = objectMapper.createObjectNode();
      params.put("user_password", "s3cr3t-value");
      params.put("client_secret", "another-secret");
      ObjectNode signature = objectMapper.createObjectNode();
      signature.put("method", "io.openaev.rest.user.UserApi.createUser");
      signature.set("parameters", params);

      // Act
      logService.logRequestEvent(
          "create",
          "success",
          ResourceType.USER,
          UUID.randomUUID().toString(),
          null,
          null,
          signature,
          null,
          Level.INFO,
          UUID.randomUUID().toString());

      // Assert
      String emitted = dispatchedSignature().toString();
      assertThat(emitted).doesNotContain("s3cr3t-value").doesNotContain("another-secret");
    }
  }

  @Nested
  @DisplayName("logGenericEvent — processMutationContext")
  class LogGenericEventSignature {

    @Test
    @DisplayName("an oversized signature in the context is capped before emission")
    void given_oversizedSignatureInContext_should_capEmittedSignature() {
      // Arrange
      Map<String, Object> ctx = new LinkedHashMap<>();
      ctx.put("signature", oversizedSignature());

      AuditEvent event =
          AuditEvent.builder()
              .eventType(EventType.MUTATION)
              .eventScope(AuditEventScope.CREATE)
              .eventStatus(EventStatus.SUCCESS)
              .resourceType(ResourceType.INJECTOR)
              .resourceId(UUID.randomUUID().toString())
              .contextData(ctx)
              .origin(AuditEventOrigin.REQUEST)
              .build();

      // Act
      logService.logGenericEvent(event, Level.INFO, UUID.randomUUID().toString());

      // Assert
      String emitted = dispatchedSignature().toString();
      assertThat(emitted)
          .doesNotContain(HUGE_CONTRACT_CONTENT)
          .satisfiesAnyOf(
              s -> assertThat(s).contains(TRUNCATED_SUFFIX),
              s -> assertThat(s).contains("\"truncated\":true"));
    }
  }
}
