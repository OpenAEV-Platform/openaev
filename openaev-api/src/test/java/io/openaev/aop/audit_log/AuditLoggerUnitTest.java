package io.openaev.aop.audit_log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.ShutdownService;
import io.openaev.database.model.ResourceType;
import io.openaev.service.LogService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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

  /** Synchronous executor so tests run deterministically on the calling thread. */
  private final Executor syncExecutor = Runnable::run;

  private AuditLogger auditLogger;

  @BeforeEach
  void setUp() {
    auditLogger =
        new AuditLogger(
            shutdownService, auditLogProperties, logService, objectMapper, syncExecutor);
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
      when(logService.logAuthEvent(
              "login", "error", "local", null, java.util.logging.Level.WARNING, "log-1"))
          .thenThrow(new RuntimeException("transport failure"));

      // Act & Assert
      assertThatThrownBy(() -> auditLogger.logAuthEvent("login", "error", "local", null, "log-1"))
          .isInstanceOf(AuditLogFailureException.class);
      verify(shutdownService).initiateShutdown();
    }

    @Test
    @DisplayName("given_logServiceReturnsFalse_should_throwAuditLogFailureException")
    void given_logServiceReturnsFalse_should_throwAuditLogFailureException() {
      // Arrange
      when(logService.logAuthEvent(
              "login", "error", "local", null, java.util.logging.Level.WARNING, "log-2"))
          .thenReturn(false);

      // Act & Assert
      assertThatThrownBy(() -> auditLogger.logAuthEvent("login", "error", "local", null, "log-2"))
          .isInstanceOf(AuditLogFailureException.class);
      verify(shutdownService).initiateShutdown();
    }

    @Test
    @DisplayName("given_logServiceReturnsTrue_should_notThrow")
    void given_logServiceReturnsTrue_should_notThrow() {
      // Arrange
      when(logService.logAuthEvent(
              "login", "success", "local", null, java.util.logging.Level.WARNING, "log-3"))
          .thenReturn(true);

      // Act & Assert — logAuthEvent is now void, just verify no exception
      assertThatCode(() -> auditLogger.logAuthEvent("login", "success", "local", null, "log-3"))
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
      when(logService.logRequestEvent(
              "update",
              "error",
              ResourceType.TEAM,
              "team-1",
              null,
              null,
              null,
              null,
              java.util.logging.Level.WARNING,
              "log-4"))
          .thenThrow(new RuntimeException("transport failure"));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  auditLogger.logAccessControlEvent(
                      "update",
                      "error",
                      ResourceType.TEAM,
                      "team-1",
                      null,
                      null,
                      null,
                      null,
                      "log-4"))
          .isInstanceOf(AuditLogFailureException.class);
      verify(shutdownService).initiateShutdown();
    }

    @Test
    @DisplayName("given_logServiceReturnsFalse_should_throwAuditLogFailureException")
    void given_logServiceReturnsFalse_should_throwAuditLogFailureException() {
      // Arrange
      when(logService.logRequestEvent(
              "update",
              "error",
              ResourceType.TEAM,
              "team-2",
              null,
              null,
              null,
              null,
              java.util.logging.Level.WARNING,
              "log-5"))
          .thenReturn(false);

      // Act & Assert
      assertThatThrownBy(
              () ->
                  auditLogger.logAccessControlEvent(
                      "update",
                      "error",
                      ResourceType.TEAM,
                      "team-2",
                      null,
                      null,
                      null,
                      null,
                      "log-5"))
          .isInstanceOf(AuditLogFailureException.class);
      verify(shutdownService).initiateShutdown();
    }

    @Test
    @DisplayName("given_logServiceReturnsTrue_should_notThrow")
    void given_logServiceReturnsTrue_should_notThrow() {
      // Arrange
      when(logService.logRequestEvent(
              "update",
              "success",
              ResourceType.TEAM,
              "team-3",
              null,
              null,
              null,
              null,
              java.util.logging.Level.WARNING,
              "log-6"))
          .thenReturn(true);

      // Act
      CompletableFuture<Boolean> future =
          auditLogger.logAccessControlEvent(
              "update", "success", ResourceType.TEAM, "team-3", null, null, null, null, "log-6");

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

      when(logService.logRequestEvent(
              "update",
              "error",
              ResourceType.TEAM,
              "team-4",
              null,
              null,
              null,
              null,
              java.util.logging.Level.WARNING,
              "log-7"))
          .thenReturn(false);

      // Act
      CompletableFuture<Boolean> future =
          auditLogger.logAccessControlEvent(
              "update", "error", ResourceType.TEAM, "team-4", null, null, null, null, "log-7");

      // Assert — no exception, just returns false
      assertThat(future.isCompletedExceptionally()).isFalse();
      assertThat(future.join()).isFalse();
    }
  }
}
