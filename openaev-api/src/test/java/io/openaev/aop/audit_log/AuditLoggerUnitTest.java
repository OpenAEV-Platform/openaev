package io.openaev.aop.audit_log;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.ResourceType;
import io.openaev.service.LogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogger unit tests")
class AuditLoggerUnitTest {

  @Mock private LogService logService;

  @Spy @InjectMocks private AuditLogger auditLogger;

  @BeforeEach
  void setUp() {
    // Never actually call System.exit in tests
    lenient().doNothing().when(auditLogger).prepareLogFailure();
    doReturn(true).when(logService).isEnabled();
  }

  @Nested
  @DisplayName("logAuthEvent - prepareLogFailure")
  class LogAuthEventFailure {

    @Test
    @DisplayName("given_logServiceThrows_should_triggerPrepareLogFailure")
    void given_logServiceThrows_should_triggerPrepareLogFailure() {
      // Arrange
      when(logService.logAuthEvent(
              "login", "error", "local", null, java.util.logging.Level.WARNING, "log-1"))
          .thenThrow(new RuntimeException("transport failure"));

      // Act
      auditLogger.logAuthEvent("login", "error", "local", null, "log-1").join();

      // Assert
      verify(auditLogger).prepareLogFailure();
    }

    @Test
    @DisplayName("given_logServiceReturnsFalse_should_triggerPrepareLogFailure")
    void given_logServiceReturnsFalse_should_triggerPrepareLogFailure() {
      // Arrange
      when(logService.logAuthEvent(
              "login", "error", "local", null, java.util.logging.Level.WARNING, "log-2"))
          .thenReturn(false);

      // Act
      auditLogger.logAuthEvent("login", "error", "local", null, "log-2").join();

      // Assert
      verify(auditLogger).prepareLogFailure();
    }

    @Test
    @DisplayName("given_logServiceReturnsTrue_should_notTriggerPrepareLogFailure")
    void given_logServiceReturnsTrue_should_notTriggerPrepareLogFailure() {
      // Arrange
      when(logService.logAuthEvent(
              "login", "success", "local", null, java.util.logging.Level.WARNING, "log-3"))
          .thenReturn(true);

      // Act
      auditLogger.logAuthEvent("login", "success", "local", null, "log-3").join();

      // Assert
      verify(auditLogger, never()).prepareLogFailure();
    }
  }

  @Nested
  @DisplayName("logAccessControlEvent - prepareLogFailure")
  class LogAccessControlEventFailure {

    @Test
    @DisplayName("given_logServiceThrows_should_triggerPrepareLogFailure")
    void given_logServiceThrows_should_triggerPrepareLogFailure() {
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

      // Act
      auditLogger
          .logAccessControlEvent(
              "update", "error", ResourceType.TEAM, "team-1", null, null, null, null, "log-4")
          .join();

      // Assert
      verify(auditLogger).prepareLogFailure();
    }

    @Test
    @DisplayName("given_logServiceReturnsFalse_should_triggerPrepareLogFailure")
    void given_logServiceReturnsFalse_should_triggerPrepareLogFailure() {
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

      // Act
      auditLogger
          .logAccessControlEvent(
              "update", "error", ResourceType.TEAM, "team-2", null, null, null, null, "log-5")
          .join();

      // Assert
      verify(auditLogger).prepareLogFailure();
    }

    @Test
    @DisplayName("given_logServiceReturnsTrue_should_notTriggerPrepareLogFailure")
    void given_logServiceReturnsTrue_should_notTriggerPrepareLogFailure() {
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
      auditLogger
          .logAccessControlEvent(
              "update", "success", ResourceType.TEAM, "team-3", null, null, null, null, "log-6")
          .join();

      // Assert
      verify(auditLogger, never()).prepareLogFailure();
    }
  }
}
