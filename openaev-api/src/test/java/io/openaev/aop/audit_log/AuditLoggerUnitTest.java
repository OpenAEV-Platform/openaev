package io.openaev.aop.audit_log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.ShutdownService;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.ResourceType;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.LogService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.PreviewFeatureService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogger unit tests")
class AuditLoggerUnitTest {

  @Mock private LogService logService;
  @Mock private AuditLogProperties auditLogProperties;
  @Mock private ShutdownService shutdownService;
  @Mock private ObjectMapper objectMapper;
  // Banner deps — not exercised by these tests but required by the constructor.
  @Mock private PreviewFeatureService previewFeatureService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private PlatformSettingsService platformSettingsService;

  /** Synchronous executor so tests run deterministically on the calling thread. */
  private final Executor syncExecutor = Runnable::run;

  private AuditLogger auditLogger;

  @BeforeEach
  void setUp() {
    auditLogger =
        new AuditLogger(
            shutdownService,
            auditLogProperties,
            logService,
            objectMapper,
            previewFeatureService,
            enterpriseEditionService,
            licenseCacheManager,
            platformSettingsService,
            syncExecutor);
    lenient().doReturn(true).when(logService).isEnabled();
    lenient().doReturn(true).when(auditLogProperties).isHaltOnFailure();
  }

  @Nested
  @DisplayName("logAuthEvent — halt-on-failure throws AuditLogFailureException")
  class LogAuthEventFailure {

    @Test
    @DisplayName("given_logServiceThrows_should_throwAuditLogFailureException")
    void given_logServiceThrows_should_throwAuditLogFailureException() {
      // Arrange
      when(logService.logGenericEvent(any(AuditEvent.class), eq(Level.WARNING), any(String.class)))
          .thenThrow(new RuntimeException("transport failure"));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  auditLogger.logAuthEvent(AuditEventScope.LOGIN, EventStatus.ERROR, "local", null))
          .isInstanceOf(AuditLogFailureException.class);
      verify(shutdownService).initiateShutdown();
    }

    @Test
    @DisplayName("given_logServiceReturnsFalse_should_throwAuditLogFailureException")
    void given_logServiceReturnsFalse_should_throwAuditLogFailureException() {
      // Arrange
      when(logService.logGenericEvent(any(AuditEvent.class), eq(Level.WARNING), any(String.class)))
          .thenReturn(false);

      // Act & Assert
      assertThatThrownBy(
              () ->
                  auditLogger.logAuthEvent(AuditEventScope.LOGIN, EventStatus.ERROR, "local", null))
          .isInstanceOf(AuditLogFailureException.class);
      verify(shutdownService).initiateShutdown();
    }

    @Test
    @DisplayName("given_logServiceReturnsTrue_should_notThrow")
    void given_logServiceReturnsTrue_should_notThrow() {
      // Arrange
      when(logService.logGenericEvent(any(AuditEvent.class), eq(Level.WARNING), any(String.class)))
          .thenReturn(true);

      // Act & Assert
      assertThatCode(
              () ->
                  auditLogger.logAuthEvent(
                      AuditEventScope.LOGIN, EventStatus.SUCCESS, "local", null))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("logAccessControlEvent — halt-on-failure throws AuditLogFailureException")
  class LogAccessControlEventFailure {

    @Test
    @DisplayName("given_logServiceThrows_should_throwAuditLogFailureException")
    void given_logServiceThrows_should_throwAuditLogFailureException() {
      // Arrange
      when(logService.logGenericEvent(any(AuditEvent.class), eq(Level.WARNING), any(String.class)))
          .thenThrow(new RuntimeException("transport failure"));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  auditLogger.logAccessControlEvent(
                      AuditEventScope.UPDATE,
                      EventStatus.ERROR,
                      ResourceType.TEAM,
                      "team-1",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(AuditLogFailureException.class);
      verify(shutdownService).initiateShutdown();
    }

    @Test
    @DisplayName("given_logServiceReturnsFalse_should_throwAuditLogFailureException")
    void given_logServiceReturnsFalse_should_throwAuditLogFailureException() {
      // Arrange
      when(logService.logGenericEvent(any(AuditEvent.class), eq(Level.WARNING), any(String.class)))
          .thenReturn(false);

      // Act & Assert
      assertThatThrownBy(
              () ->
                  auditLogger.logAccessControlEvent(
                      AuditEventScope.UPDATE,
                      EventStatus.ERROR,
                      ResourceType.TEAM,
                      "team-2",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(AuditLogFailureException.class);
      verify(shutdownService).initiateShutdown();
    }

    @Test
    @DisplayName("given_logServiceReturnsTrue_should_notThrow")
    void given_logServiceReturnsTrue_should_notThrow() {
      // Arrange
      when(logService.logGenericEvent(any(AuditEvent.class), eq(Level.WARNING), any(String.class)))
          .thenReturn(true);

      // Act
      CompletableFuture<Boolean> future =
          auditLogger.logAccessControlEvent(
              AuditEventScope.UPDATE,
              EventStatus.SUCCESS,
              ResourceType.TEAM,
              "team-3",
              null,
              null,
              null,
              null);

      // Assert
      assertThat(future.isCompletedExceptionally()).isFalse();
      assertThat(future.join()).isTrue();
    }
  }

  @Nested
  @DisplayName("prepareLogFailure — halt disabled")
  class HaltDisabled {

    @Test
    @DisplayName("given_haltOnFailureDisabled_should_notThrow")
    void given_haltOnFailureDisabled_should_notThrow() {
      // Arrange
      doReturn(false).when(auditLogProperties).isHaltOnFailure();

      when(logService.logGenericEvent(any(AuditEvent.class), eq(Level.WARNING), any(String.class)))
          .thenReturn(false);

      // Act
      CompletableFuture<Boolean> future =
          auditLogger.logAccessControlEvent(
              AuditEventScope.UPDATE,
              EventStatus.ERROR,
              ResourceType.TEAM,
              "team-4",
              null,
              null,
              null,
              null);

      // Assert — no exception, just returns false
      assertThat(future.isCompletedExceptionally()).isFalse();
      assertThat(future.join()).isFalse();
    }
  }
}
